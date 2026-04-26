cmake_minimum_required(VERSION 3.22.1)

project(SteamBootstrap C)

add_library(steambootstrap SHARED steam_bootstrap.c)

target_link_libraries(steambootstrap log dl)
