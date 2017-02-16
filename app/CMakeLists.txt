# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             jni

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             src/main/cpp/jni.c )

# one day we will be able to replace those lines with find_library(vorbis-lib vorbis)
add_library(vorbis SHARED IMPORTED)
string(REPLACE "/app/intermediates/cmake/" "/libvorbis/intermediates/cmake/library/" VORBIS_TARGET "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libvorbis.so")
include_directories(../libvorbis/src/main/cpp/include)
set_target_properties(vorbis PROPERTIES IMPORTED_LOCATION ${VORBIS_TARGET})

add_library(ogg SHARED IMPORTED)
string(REPLACE "/app/intermediates/cmake/" "/libogg/intermediates/cmake/library/" OGG_TARGET "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libogg.so")
include_directories(../libogg/src/main/cpp/include)
set_target_properties(ogg PROPERTIES IMPORTED_LOCATION ${OGG_TARGET})

include_directories(src/main/cpp)

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
                       jni

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} vorbis ogg)

