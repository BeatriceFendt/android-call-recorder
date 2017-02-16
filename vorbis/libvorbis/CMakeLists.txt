# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             vorbis

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             src/main/cpp/analysis.c
             src/main/cpp/barkmel.c
             src/main/cpp/bitrate.c
             src/main/cpp/block.c
             src/main/cpp/codebook.c
             src/main/cpp/envelope.c
             src/main/cpp/floor0.c
             src/main/cpp/floor1.c
             src/main/cpp/info.c
             src/main/cpp/lookup.c
             src/main/cpp/lpc.c
             src/main/cpp/lsp.c
             src/main/cpp/mapping0.c
             src/main/cpp/mdct.c
             src/main/cpp/psy.c
             src/main/cpp/registry.c
             src/main/cpp/res0.c
             src/main/cpp/sharedbook.c
             src/main/cpp/smallft.c
             src/main/cpp/synthesis.c
             src/main/cpp/vorbisenc.c
             src/main/cpp/vorbisfile.c
             src/main/cpp/window.c )

# one day we will be able to replace those lines with find_library(ogg-lib ogg)
add_library(ogg SHARED IMPORTED)
string(REPLACE "/libvorbis/" "/libogg/" OGG_TARGET "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libogg.so")
include_directories(../libogg/src/main/cpp/include)
set_target_properties(ogg PROPERTIES IMPORTED_LOCATION ${OGG_TARGET})

include_directories(src/main/cpp src/main/cpp/include)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because system libraries are included in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       vorbis

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} ogg )
