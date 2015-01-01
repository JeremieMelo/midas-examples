#include "debug_api.h"
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>

class Tile_t: debug_api_t
{
public:
  Tile_t(int argc, char** argv): debug_api_t("Tile", false, false) {
    std::string filename;
    for (int i = 0 ; i < argc ; i++) {
      std::string arg = argv[i];
      if (arg.substr(0, 12) == "+max-cycles=") {
        timeout = atoll(argv[i]+12);
      } else if (arg.substr(0, 9) == "+loadmem=") {
        filename = argv[i]+9;
      }
    }
    testname = filename.substr(filename.rfind("/")+1);

    load_mem(filename);
    open_snap(testname + ".snap");
  }
  void run() {
    do {
      step(50);
    } while (peek("Tile.io_htif_host_tohost") == 0 && t < timeout);
    uint64_t tohost = peek("Tile.io_htif_host_tohost");
    std::ostringstream reason;
    std::ostringstream result;
    if (t > timeout) {
      reason << "timeout";
      result << "FAILED";
    } else if (tohost != 1) {
      reason << "tohost = " << tohost;
      result << "FAILED";
    } else {
      reason << "tohost = " << tohost;
      result <<"PASSED";
    }
    std::cout << "ISA: " << testname << std::endl;
    std::cout << "*** " << result.str() << " *** (" << reason.str() << ") ";
    std::cout << "after " << t << " simulation cycles" << std::endl;
  }
private:
  std::string testname;
  uint64_t timeout;
};

int main(int argc, char** argv) {
  Tile_t Tile(argc, argv);
  Tile.run();
  return 0;
}
