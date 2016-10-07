#include "simif_zynq.h"

class GCD_t: simif_zynq_t
{
public:
  GCD_t(int argc, char** argv):
    simif_zynq_t(argc, argv, true) { }

  virtual int run() {
    uint32_t a = 64, b = 48, z = 16; //test vectors
    do {
      poke(io_a, a);
      poke(io_b, b);
      poke(io_e, cycles() == 0 ? 1 : 0);
      step(1);
    } while (cycles() <= 1 || peek(io_v) == 0);
    expect(io_z, z);
    return exitcode();
  }
};

int main(int argc, char** argv) 
{
  GCD_t GCD(argc, argv);
  return GCD.run();
}
