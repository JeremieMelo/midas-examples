#include "simif.h"

class ResetShiftRegister_t: public virtual simif_t
{
public:
  void run() {
    std::vector<uint32_t> ins(5, 0);
    int k = 0;
    for (int i = 0 ; i < 16 ; i++) {
      uint32_t in    = rand_next(16);
      uint32_t shift = rand_next(2);
      if (shift == 1) ins[k % 5] = in;
      poke(io_in,    in);
      poke(io_shift, shift);
      step(1);
      expect(io_out, cycles() < 4 ? 0 : ins[(k + 1) % 5]);
      if (shift == 1) k++;
    }
  }
};