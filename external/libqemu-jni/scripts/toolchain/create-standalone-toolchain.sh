#!/usr/bin/env bash

set -e -u

if [ $# -ne 2 ]; then
	echo "Usage: create-standalone-toolchain.sh [android ndk dir] [toolchain dir]"
	echo "Generate toolchain from the Android NDK and save it to specified directory."
	exit 1
fi

ANDROID_NDK_DIR=$1
STANDALONE_TOOLCHAIN_DIR=$2
STANDALONE_TOOLCHAIN_DIR_STAGING="${2}.tmp"
SCRIPT_DIR=$(dirname "$(realpath "$0")")

rm -rf "$STANDALONE_TOOLCHAIN_DIR_STAGING"

cp -r "$ANDROID_NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64" \
	"$STANDALONE_TOOLCHAIN_DIR_STAGING"

# Remove android-support header wrapping not needed on android-21:
rm -Rf "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot/usr/local"

for api in {16..29}; do
	for host_plat in aarch64-linux-android armv7a-linux-androideabi i686-linux-android x86_64-linux-android; do
		if [ -e "$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-clang" ]; then
			# Setup c/c++ preprocessor.
			cp "$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-clang" \
				"$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-cpp"
			sed -i 's/clang/clang -E/' \
				"$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-cpp"

			# GCC link required by CMake.
			ln -sfr "$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-clang" \
				"$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-gcc"
		fi

		if [ -e "$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-clang++" ]; then
			# GCC link required by CMake.
			ln -sfr "$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-clang++" \
				"$STANDALONE_TOOLCHAIN_DIR_STAGING/bin/${host_plat}${api}-g++"
		fi
	done
done
unset api host_plat

(cd "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot"
	for f in "$SCRIPT_DIR"/ndk-patches/*.patch; do
		patch --silent -p1 -i $f
	done
)

# langinfo.h: Inline implementation of nl_langinfo().
cp "$SCRIPT_DIR"/ndk-headers/{libintl.h,langinfo.h} "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot/usr/include/"

# Remove <sys/capability.h> because it is provided by libcap.
# Remove <sys/shm.h> from the NDK in favour of that from the libandroid-shmem.
# Remove <sys/sem.h> as it doesn't work for non-root.
# Remove <glob.h> as we currently provide it from libandroid-glob.
# Remove <spawn.h> as it's only for future (later than android-27).
# Remove <zlib.h> and <zconf.h> as we build our own zlib
rm -f "$STANDALONE_TOOLCHAIN_DIR_STAGING"/sysroot/usr/include/sys/{capability.h,shm.h,sem.h} \
	"$STANDALONE_TOOLCHAIN_DIR_STAGING"/sysroot/usr/include/{glob.h,spawn.h,zlib.h,zconf.h}

# We want static libc++.
for HOST_PLAT in aarch64-linux-android arm-linux-androideabi i686-linux-android x86_64-linux-android; do
	if [ "$HOST_PLAT" = "arm-linux-androideabi" ]; then
		echo "INPUT(-lc++_static -lc++abi -lunwind)" \
			> "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot/usr/lib/$HOST_PLAT/libc++_shared.so"
	else
		echo "INPUT(-lc++_static -lc++abi)" \
			> "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot/usr/lib/$HOST_PLAT/libc++_shared.so"
	fi
done

# Ensure that we use our libz.{so,a} instead of NDK's.
find "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot/usr/lib" -type f -iname "libz.*" -delete

grep -lrw "$STANDALONE_TOOLCHAIN_DIR_STAGING/sysroot/usr/include/c++/v1" -e '<version>' | xargs -n 1 sed -i 's/<version>/\"version\"/g'

mv "$STANDALONE_TOOLCHAIN_DIR_STAGING" "$STANDALONE_TOOLCHAIN_DIR"
