cmake_minimum_required(VERSION 3.22.1)
project(vcmosh)

add_library(vcmosh SHARED mosh_udp.cpp)
find_library(log-lib log)
target_link_libraries(vcmosh ${log-lib})
