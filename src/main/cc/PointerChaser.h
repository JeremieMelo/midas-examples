//See LICENSE for license details.

#include "simif.h"
#include "endpoints/sim_mem.h"
#include "endpoints/fpga_memory_model.h"

class PointerChaser_t: virtual simif_t
{
public:
  PointerChaser_t(int argc, char** argv) {
    max_cycles = 20000L;
#ifndef _WIN32
    mpz_inits(address, result, NULL);
    mpz_set_ui(address, 64L);
    mpz_set_ui(result, 1176L);
#else
    address = 64L;
    result = 1176L;
#endif
    std::vector<std::string> args(argv + 1, argv + argc);
    for (auto &arg: args) {
      if (arg.find("+max-cycles=") == 0) {
        max_cycles = atoi(arg.c_str()+12);
      }
      if (arg.find("+address=") == 0) {
#ifndef _WIN32
        mpz_set_ui(address, atoll(arg.c_str() + 9));
#else
        address = atoll(arg.c_str() + 9);
#endif
      }
      if (arg.find("+result=") == 0) {
#ifndef _WIN32
        mpz_set_ui(result, atoll(arg.c_str() + 9));
#else
        result = atoi(arg.c_str() + 9);
#endif
      }
    }
#ifdef NASTIWIDGET_0
    endpoints.push_back(new sim_mem_t(this, argc, argv));
#endif

#ifdef MEMMODEL_0
    fpga_models.push_back(new FpgaMemoryModel(
        this,
        // Casts are required for now since the emitted type can change...
        AddressMap(MEMMODEL_0_R_num_registers,
                   (const unsigned int*) MEMMODEL_0_R_addrs,
                   (const char* const*) MEMMODEL_0_R_names,
                   MEMMODEL_0_W_num_registers,
                   (const unsigned int*) MEMMODEL_0_W_addrs,
                   (const char* const*) MEMMODEL_0_W_names),
        argc, argv, "memory_stats.csv"));
#endif
  }

  void run() {
    for (auto e: fpga_models) {
      e->init();
    }
    target_reset(0);

    poke(io_startAddr_bits, address);
    poke(io_startAddr_valid, 1);
    do {
      step(1);
    } while (!peek(io_startAddr_ready));
    poke(io_startAddr_valid, 0);
    poke(io_result_ready, 1);
    do {
      step(1, false);
      bool _done;
      do {
        _done = done();
        for (auto e: endpoints) {
          _done &= e->done();
          e->tick();
        }
      } while(!_done);
    } while (!peek(io_result_valid) && cycles() < max_cycles);
    expect(io_result_bits, result);
  }
private:
  std::vector<endpoint_t*> endpoints;
  std::vector<FpgaModel*> fpga_models;
  uint64_t max_cycles;
  mpz_t address;
  mpz_t result; // 64 bit
};
