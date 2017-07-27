include Makefrag

PLATFORM ?= zynq
# PLATFORM ?= catapult
DEBUG ?=
LOADMEM ?=
LOGFILE ?=
WAVEFORM ?=
BOARD ?=
SAMPLE ?=
MACROLIB ?=
ARGS ?=
DRIVER ?=

debug = $(if $(DEBUG),DEBUG=$(DEBUG),)
loadmem = $(if $(LOADMEM),LOADMEM=$(LOADMEM),)
logfile = $(if $(LOGFILE),LOGFILE=$(LOGFILE),)
waveform = $(if $(WAVEFORM),WAVEFORM=$(WAVEFORM),)
sample = $(if $(SAMPLE),SAMPLE=$(SAMPLE),)
args = $(if $(ARGS),ARGS="$(ARGS)",)
macrolib = $(if $(MACROLIB),MACROLIB=$(MACROLIB),)
board = $(if $(BOARD),BOARD=$(BOARD),)

# Desings
designs := GCD Parity ShiftRegister ResetShiftRegister EnableShiftRegister \
	Stack Risc RiscSRAM PointerChaser Tile

# Tests
verilator = $(addsuffix -verilator, $(designs))
$(verilator): %-verilator:
	$(MAKE) -C $(base_dir) -f test.mk verilator PLATFORM=$(PLATFORM) DESIGN=$* \
	$(debug) $(macrolib)

verilator_test = $(addsuffix -verilator-test, $(designs))
$(verilator_test): %-verilator-test:
	$(MAKE) -C $(base_dir) -f test.mk verilator-test PLATFORM=$(PLATFORM) DESIGN=$* \
	$(debug) $(loadmem) $(logfile) $(waveform) $(sample) $(args) $(macrolib)

vcs = $(addsuffix -vcs, $(designs))
$(vcs): %-vcs:
	$(MAKE) -C $(base_dir) -f test.mk vcs PLATFORM=$(PLATFORM) DESIGN=$* \
	$(debug) $(macrolib)

vcs_test = $(addsuffix -vcs-test, $(designs))
$(vcs_test): %-vcs-test:
	$(MAKE) -C $(base_dir) -f test.mk vcs-test PLATFORM=$(PLATFORM) DESIGN=$* \
	$(debug) $(loadmem) $(logfile) $(waveform) $(sample) $(args) $(macrolib)

# FPGA
$(PLATFORM) = $(addsuffix -$(PLATFORM), $(designs))
$($(PLATFORM)): %-$(PLATFORM):
	$(MAKE) -C $(base_dir) -f fpga.mk $(PLATFORM) PLATFORM=$(PLATFORM) DESIGN=$* \
	$(board) $(macrolib) DRIVER=$(DRIVER) 

fpga = $(addsuffix -fpga, $(designs))
$(fpga): %-fpga:
	$(MAKE) -C $(base_dir) -f fpga.mk fpga PLATFORM=$(PLATFORM) DESIGN=$* \
	$(board) $(macrolib)

# Replays
vcs_rtl = $(addsuffix -vcs-rtl, $(designs))
$(vcs_rtl): %-vcs-rtl:
	$(MAKE) -C $(base_dir) -f replay.mk vcs-rtl PLATFORM=$(PLATFORM) DESIGN=$* \
	$(macrolib)

replay_rtl = $(addsuffix -replay-rtl, $(designs))
$(replay_rtl): %-replay-rtl:
	$(MAKE) -C $(base_dir) -f replay.mk replay-rtl PLATFORM=$(PLATFORM) DESIGN=$* \
	$(sample) $(logfile) $(waveform) $(macrolib)

vcs_syn = $(addsuffix -vcs-syn, $(designs))
$(vcs_syn): %-vcs-syn:
	$(MAKE) -C $(base_dir) -f replay.mk vcs-syn PLATFORM=$(PLATFORM) DESIGN=$* \
	$(macrolib)

replay_syn = $(addsuffix -replay-syn, $(designs))
$(replay_syn): %-replay-syn:
	$(MAKE) -C $(base_dir) -f replay.mk replay-syn PLATFORM=$(PLATFORM) DESIGN=$* \
	$(sample) $(logfile) $(waveform) $(macrolib)

vcs_par = $(addsuffix -vcs-par, $(designs))
$(vcs_par): %-vcs-par:
	$(MAKE) -C $(base_dir) -f replay.mk vcs-par PLATFORM=$(PLATFORM) DESIGN=$*

replay_par = $(addsuffix -replay-par, $(designs))
$(replay_par): %-replay-par:
	$(MAKE) -C $(base_dir) -f replay.mk replay-par PLATFORM=$(PLATFORM) DESIGN=$* \
	$(sample) $(logfile) $(waveform) $(macrolib)

# Clean
design_mostlyclean = $(addsuffix -mostlyclean, $(designs))
$(design_mostlyclean): %-mostlyclean:
	$(MAKE) -C $(base_dir) -f test.mk mostlyclean PLATFORM=$(PLATFORM) DESIGN=$*
	$(MAKE) -C $(base_dir) -f replay.mk mostlyclean PLATFORM=$(PLATFORM) DESIGN=$*

design_clean = $(addsuffix -clean, $(designs))
$(design_clean): %-clean:
	$(MAKE) -C $(base_dir) -f test.mk clean PLATFORM=$(PLATFORM) DESIGN=$*
	$(MAKE) -C $(base_dir) -f replay.mk clean PLATFORM=$(PLATFORM) DESIGN=$*

mostlyclean: $(design_mostlyclean)

clean: $(design_clean)

.PHONY: $(verilator) $(verilator_test) $(vcs) $(vcs_test) $($(PLATFORM)) $(fpga)
.PHONY: $(vcs_rtl) $(replay_rtl) $(vcs_syn) $(replay_syn) $(vcs_par) $(replay_par)
.PHONY: $(design_mostlyclean) $(design_clean) mostlyclean clean
