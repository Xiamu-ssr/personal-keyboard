#include <cstdio>
#include <random>
#include <string>
#include "pinyinime.h"

int main(int argc, char** argv) {
  if (argc != 3 || !ime_pinyin::im_open_decoder(argv[1], argv[2])) return 2;
  auto check = [](const std::string& raw) {
    ime_pinyin::im_reset_search();
    auto count = ime_pinyin::im_search(raw.c_str(), raw.size());
    for (size_t i = 0; i < count && i < 20; ++i) {
      ime_pinyin::char16 value[256] = {0};
      ime_pinyin::im_get_candidate(i, value, 256);
    }
  };
  for (char ch = 'a'; ch <= 'z'; ++ch)
    for (int length = 1; length <= 39; ++length) check(std::string(length, ch));
  std::mt19937 random(12345);
  for (int i=0; i<1000; ++i) {
    std::string raw(1 + random()%39, 'a');
    for (char& ch : raw) ch = 'a'+random()%26;
    check(raw);
  }
  ime_pinyin::im_close_decoder();
  std::puts("2014 native boundary cases passed");
}
