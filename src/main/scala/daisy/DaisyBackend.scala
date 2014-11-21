package Daisy

import Chisel._
import scala.collection.mutable.{ArrayBuffer, HashMap, HashSet}
import addDaisyPins._

object DaisyBackend { 
  val states = HashMap[Module, ArrayBuffer[Node]]()
  val srams  = HashMap[Module, ArrayBuffer[Mem[_]]]()
  val sramAddrs = HashMap[Mem[_], Reg]()
  lazy val top = Driver.topComponent.asInstanceOf[DaisyShim[Module]]
  lazy val targetName = Driver.backend.extractClassName(top.target)
  lazy val targetComps = Driver.sortedComps filterNot (x => 
    x.name == top.name || (x.name != top.target.name && (top.children contains x)))
  var daisywidth = -1

  def addTransforms(width: Int) {
    daisywidth = width
    Driver.backend.transforms ++= Seq(
      initDaisy,
      Driver.backend.findConsumers,
      Driver.backend.inferAll,
      connectStallSignals,
      addStateChains,
      addSRAMChain,
      printOutMappings
    )
  } 

  def initDaisy(c: Module) {
    top.name = targetName + "Shim"
    for (m <- targetComps ; if m.name != top.target.name) {
      addDaisyPins(m, daisywidth)
    }
  }

  // Connect the stall signal to the register and memory writes for freezing
  def connectStallSignals(c: Module) {
    ChiselError.info("[DaisyBackend] connect stall signals")

    def connectStallPins(m: Module) {
      if (m.name != top.target.name && daisyPins(m).stall.inputs.isEmpty) {
        connectStallPins(m.parent)
        daisyPins(m).stall := daisyPins(m.parent).stall
      }
    }

    for (m <- targetComps) {
      states(m) = ArrayBuffer[Node]()
      srams(m) = ArrayBuffer[Mem[_]]()
      connectStallPins(m)
      // Add target's inputs
      if (m.name == top.target.name) states(m) ++= top.inputs
      m bfs { _ match {
        case reg: Reg => { 
          connectStallPins(m)
          reg.inputs(0) = Multiplex(daisyPins(m).stall, reg, reg.inputs(0))
          // Add the register for daisy chains
          states(m) += reg
        }
        case mem: Mem[_] => {
          connectStallPins(m)
          for (write <- mem.writeAccesses) {
            write cond_= write.cond.asInstanceOf[Bool] && !daisyPins(m).stall
          }
          if (mem.seqRead) {
            srams(m) += mem
          } else {
            for (i <- 0 until mem.size) {
              val read = new MemRead(mem, UInt(i)) 
              read.infer
              states(m) += read
            }
          }
        }
        case _ =>
      } }
    }
  }

  def addStateChains(c: Module) {
    ChiselError.info("[DaisyBackend] add state chains")

    val hasStateChain = HashSet[Module]()
    def insertStateChain(m: Module) = {
      val datawidth = (states(m) foldLeft 0)(_ + _.needWidth)
      val chain = if (!states(m).isEmpty) m.addModule(new StateChain, {case DataWidth => datawidth}) else null
      if (chain != null) {
        var stateIdx = 0
        var stateOff = 0
        for (i <- (0 until chain.daisylen).reverse) {
          val wires = ArrayBuffer[UInt]()
          var totalWidth = 0
          while (totalWidth < daisywidth) {
            val totalMargin = daisywidth - totalWidth
            if (stateIdx < states(m).size) {
              val state = states(m)(stateIdx)
              val stateWidth = state.needWidth
              val stateMargin = stateWidth - stateOff
              if (stateMargin <= totalMargin) {
                wires += UInt(state)(stateMargin-1, 0)
                totalWidth += stateMargin
                stateOff = 0
                stateIdx += 1
              } else {
                wires += UInt(state)(stateMargin-1, stateMargin-totalMargin)
                totalWidth += totalMargin
                stateOff += totalMargin
              }
            } else {
              wires += UInt(0, totalMargin)
              totalWidth += totalMargin 
            }
            chain.io.dataIo.data(i) := Cat(wires) 
          }
        }
        chain.io.dataIo.out <> daisyPins(m).state.out
        chain.io.stall := daisyPins(m).stall
        hasStateChain += m
      } 
      chain
    }

    for (m <- targetComps) {
      val stateChain = insertStateChain(m)
      // Filter children who have state chains
      var last = -1
      for ((child, cur) <- m.children.zipWithIndex ; if hasStateChain contains child) {
        if (last < 0) {
          if (stateChain != null) {
            stateChain.io.dataIo.in <> daisyPins(child).state.out
          } else {
            daisyPins(m).state.out <> daisyPins(child).state.out
          }
        } else {
          daisyPins(m.children(last)).state.in <> daisyPins(child).state.out
        }
        last = cur
      }

      if (last > -1) {
        hasStateChain += m
        daisyPins(m.children(last)).state.in <> daisyPins(m).state.in
      } else if (stateChain != null) {
        stateChain.io.dataIo.in <> daisyPins(m).state.in
      }
    }
  } 

  def addSRAMChain(c: Module) {
    ChiselError.info("[DaisyBackend] add sram chains")

    def connectSRAMRestarts(m: Module) {
      if (m.name != top.target.name && daisyPins(m).stall.inputs.isEmpty) {
        connectSRAMRestarts(m.parent)
        daisyPins(m).sram.restart := daisyPins(m.parent).sram.restart
      }
    }

    val hasSRAMChain = HashSet[Module]()
    def insertSRAMChain(m: Module) = {
      var lastChain: SRAMChain = null
      for (sram <- srams(m)) {
        var addr: Reg = null
        var data: Node = null
        for (read <- sram.readAccesses) {
          read match {
            case mr: MemRead => {
              mr.addr.getNode match {
                case addrReg: Reg => {
                  addr = addrReg
                  data = mr
                }
                case _ =>
              }
            }
            case msr: MemSeqRead => {
              addr = msr.addrReg
              data = msr
            }
            case _ =>
          }
        }
        val datawidth = sram.needWidth
        val chain = m.addModule(new SRAMChain, {
          case DataWidth => datawidth 
          case SRAMSize => sram.size})
        chain.io.stall := daisyPins(m).stall
        var high = datawidth-1
        for (i <- (0 until chain.daisylen).reverse) {
          val low = math.max(high-daisywidth+1, 0)
          val widthMargin = daisywidth-(high-low+1)
          val thisData = UInt(data)(high, low)
          if (widthMargin == 0) {
            chain.io.dataIo.data(i) := thisData
          } else {
            chain.io.dataIo.data(i) := Cat(thisData, UInt(0, widthMargin))
          }
          high -= daisywidth
        }
        if (lastChain == null) {
          connectSRAMRestarts(m)
          daisyPins(m).sram.out <> chain.io.dataIo.out
        } else {
          lastChain.io.dataIo.in <> chain.io.dataIo.out
        }
        chain.io.restart := daisyPins(m).sram.restart
        // Connect chain addr to SRAM addr
        chain.io.addrIo.in := UInt(addr)
        addr.inputs(0) = Multiplex(chain.io.addrIo.out.valid, 
                                   chain.io.addrIo.out.bits, addr.inputs(0))
        lastChain = chain
      }
      if (lastChain != null) hasSRAMChain += m
      lastChain
    }

    if (Driver.hasSRAM) {
      for (m <- targetComps) {
        val sramChain = insertSRAMChain(m)   
        // Filter children who have sram chains
        var last = -1
        for ((child, cur) <- m.children.zipWithIndex; if hasSRAMChain contains child) {
          if (last < 0) {
            if (sramChain != null) {
              sramChain.io.dataIo.in <> daisyPins(child).sram.out
            } else {
              connectSRAMRestarts(m)
              daisyPins(m).sram.out <> daisyPins(child).sram.out
            }
          } else {
            daisyPins(m.children(last)).sram.in <> daisyPins(child).sram.out
          }
          last = cur
        }

        if (last > -1) {
          hasSRAMChain += m
          daisyPins(m).sram.in <> daisyPins(m.children(last)).sram.in
        } else if (sramChain != null) {
          daisyPins(m).sram.in <> sramChain.io.dataIo.in
        }     
      } 
    }
  }

  def printOutMappings(c: Module) {
    ChiselError.info("[DaisyBackend] print out chain mappings")
    val prefix = top.name + "." + top.target.name
    val res = new StringBuilder

    val ioFile = Driver.createOutputFile(targetName + ".io.map")
    // Print out parameters
    res append "HOSTWIDTH: %d\n".format(top.hostwidth)
    res append "OPWIDTH: %d\n".format(top.opwidth)
    res append "ADDRWIDTH: %d\n".format(top.addrwidth)
    res append "MEMWIDTH: %d\n".format(top.memwidth)
    res append "STEP: %d\n".format(top.STEP.litValue())
    res append "POKE: %d\n".format(top.POKE.litValue())
    res append "PEEK: %d\n".format(top.PEEK.litValue())
    res append "SNAP: %d\n".format(top.SNAP.litValue())
    res append "MEM: %d\n".format(top.MEM.litValue())

    // Print out the IO mapping for pokes and peeks
    res append "INPUT:\n"
    var inputNum = 0
    for (input <- top.inputs) {
      val path = targetName + "." + (top.target.getPathName(".") stripPrefix prefix) + input.name
      val width = input.needWidth
      val n = (width - 1) / top.hostwidth + 1
      res append "%s %d\n".format(path, width)
    }
    res append "OUTPUT:\n"
    for (output <- top.outputs) {
      val path = targetName + "." + (top.target.getPathName(".") stripPrefix prefix) + output.name
      val width = output.needWidth
      val n = (width - 1) / top.hostwidth + 1
      res append "%s %d\n".format(path, width)
    }
    try {
      ioFile write res.result
    } finally {
      ioFile.close
      res.clear
    }

    // Print out the chain mapping
    val chainFile = Driver.createOutputFile(targetName + ".chain.map")
    // Collect states
    var stateWidth = 0
    var totalWidth = 0
    for (m <- targetComps) {
      var daisyWidth = 0
      var localWidth = 0
      for (state <- states(m)) {
        state match {
          case read: MemRead => {
            val mem = read.mem
            val addr = read.addr.litValue(0).toInt
            val path = targetName + (m.getPathName(".") stripPrefix prefix) + "." + mem.name
            val width = mem.needWidth
            res append "%s[%d] %d\n".format(path, addr, width)
            stateWidth += width
            localWidth += width
            while (totalWidth < stateWidth) totalWidth += top.hostwidth
            while (daisyWidth < localWidth) daisyWidth += daisywidth
          }
          case _ => { 
            val path = targetName + (m.getPathName(".") stripPrefix prefix) + "." + state.name
            val width = state.needWidth
            res append "%s %d\n".format(path, width)
            stateWidth += width
            localWidth += width
            while (totalWidth < stateWidth) totalWidth += top.hostwidth
            while (daisyWidth < localWidth) daisyWidth += daisywidth
          }
        }
      }
      val daisyPadWidth = daisyWidth - localWidth
      if (daisyPadWidth > 0) {
        res append "null %d\n".format(daisyPadWidth)
        stateWidth += daisyPadWidth
      }
    }
    val totalPadWidth = totalWidth - stateWidth
    if (totalPadWidth > 0) {
      res append "null %d\n".format(totalPadWidth)
    }

    for (i <- 0 until Driver.sramMaxSize ; m <- targetComps.reverse) {
      for (sram <- srams(m)) {
        val path = targetName + (m.getPathName(".") stripPrefix prefix) + "." + sram.name
        val width = sram.needWidth
        var daisyWidth = 0
        if (i < sram.n) 
          res append "%s[%d] %d\n".format(path, i, width)
        else 
          res append "null %d\n".format(width)
        while (daisyWidth < width) daisyWidth += daisywidth
        val daisyPadWidth = daisyWidth - width
        if (daisyPadWidth > 0) {
          res append "null %d\n".format(daisyPadWidth)  
        }
      }
    }
    try {
      chainFile write res.result
    } finally {
      chainFile.close
      res.clear
    }
  }
}