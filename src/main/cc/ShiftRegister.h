#include "simif.h"

class ShiftRegister_t: virtual simif_t
{
public:
  void run() {
    std::vector<uint32_t> reg(4);
    target_reset();
    for (int i = 0 ; i < 64 ; i++) {
      uint32_t in = rand_next(2);
      poke(io_in, in);
      step(1);
      if (cycles() > 4) expect(io_out, reg[3]);
      for (int j = 3 ; j > 0 ; j--) reg[j] = reg[j-1];
      reg[0] = in;
    } 
  }
};
