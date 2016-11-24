###########################
#     Strober Tests       #
###########################

DESIGN ?= Tile
PLATFORM ?= zynq

include Makefrag
include Makefrag-strober

DEBUG ?=
LOADMEM ?=
SAMPLE ?=
LOGFILE ?=
WAVEFORM ?=
ARGS ?= +fastloadmem

debug = $(if $(DEBUG),-debug,)
loadmem = $(if $(LOADMEM),+loadmem=$(abspath $(LOADMEM)),)
prefix = $(notdir $(basename $(if $(LOADMEM),$(notdir $(LOADMEM)),$(DESIGN))))
sample = $(if $(SAMPLE),$(abspath $(SAMPLE)),$(out_dir)/$(prefix).sample)
logfile = $(if $(LOGFILE),$(abspath $(LOGFILE)),$(out_dir)/$(prefix).$1.out)
waveform = $(if $(WAVEFORM),$(abspath $(WAVEFORM)),$(out_dir)/$(prefix).$1)

# Compile Verilator
$(gen_dir)/V$(DESIGN)$(debug): $(testbench_dir)/$(DESIGN)-emul.cc $(testbench_dir)/$(DESIGN).h \
	$(gen_dir)/$(shim).v $(simif_cc) $(simif_h) $(gen_dir)/dramsim2_ini
	$(MAKE) -C $(simif_dir) verilator$(debug) DESIGN=$(DESIGN) GEN_DIR=$(gen_dir) TESTBENCH=$<
verilator: $(gen_dir)/V$(DESIGN)$(debug)

# Run Veriltor test
verilator-test: $(gen_dir)/V$(DESIGN)$(debug)
	mkdir -p $(out_dir)
	cd $(gen_dir) && ./$(notdir $<) $(ARGS) $(loadmem) +dramsim +sample=$(sample) \
	+waveform=$(call waveform,vcd) 2> $(call logfile,verilator)

# Compile VCS
$(gen_dir)/$(DESIGN)$(debug): $(testbench_dir)/$(DESIGN)-emul.cc $(testbench_dir)/$(DESIGN).h \
	$(gen_dir)/$(shim).v $(simif_cc) $(simif_h) $(gen_dir)/dramsim2_ini
	$(MAKE) -C $(simif_dir) vcs$(debug) DESIGN=$(DESIGN) GEN_DIR=$(gen_dir) TESTBENCH=$<
vcs: $(gen_dir)/$(DESIGN)$(debug)

# Run VCS test
vcs-test: $(gen_dir)/$(DESIGN)$(debug)
	mkdir -p $(out_dir)
	cd $(gen_dir) && ./$(notdir $<) $(ARGS) $(loadmem) +dramsim +sample=$(sample) \
	+waveform=$(call waveform,vpd) 2> $(call logfile,vcs)

mostlyclean:
	rm -rf $(gen_dir)/V$(DESIGN)$(debug) $(gen_dir)/V$(DESIGN)$(debug).csrc 
	rm -rf $(gen_dir)/$(DESIGN)$(debug) $(gen_dir)/$(DESIGN)$(debug).csrc $(gen_dir)/$(DESIGN)$(debug).daidir
	rm -rf $(out_dir)

clean:
	rm -rf $(gen_dir) $(out_dir)

.PHONY: verilator verilator-test vcs vcs-test mostlyclaen clean
