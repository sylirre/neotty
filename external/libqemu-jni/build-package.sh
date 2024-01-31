#!/bin/bash

set -e -o pipefail -u

export PACKAGE_API_LEVEL="24"
export PACKAGE_TARGET_ARCH="aarch64"
export PACKAGE_INSTALL_PREFIX="/data/data/app.neotty/files"

: "${CONFIG_BUILDER_MAKE_PROCESSES:="$(nproc)"}"
CONFIG_BUILDER_DEBUG="false"
CONFIG_BUILDER_FORCE="false"
CONFIG_BUILDER_SKIP_DEPCHECK="false"

BUILDER_SCRIPTDIR=$(dirname "$(realpath "$0")")
export BUILDER_SCRIPTDIR

export BUILDER_TOPDIR="$HOME/.cache/package_builder"
export CROSS_TOOLCHAIN_DIR="$HOME/ndk-llvm-x86_64"
export PATH="$PATH:$CROSS_TOOLCHAIN_DIR/bin"

# Utility function to log an error message and exit with an error code.
error_exit() {
	echo "ERROR: $*"
	exit 1
}

# Utility function to download a resource with an expected checksum.
url_download() {
	if [ $# != 3 ]; then
		error_exit "url_download(): Invalid arguments - expected \$URL \$DESTINATION \$CHECKSUM"
	fi

	local URL="$1"
	local DESTINATION="$2"
	local CHECKSUM="$3"

	if [ -f "$DESTINATION" ] && [ "$CHECKSUM" != "SKIP_CHECKSUM" ]; then
		# Keep existing file if checksum matches.
		local EXISTING_CHECKSUM
		EXISTING_CHECKSUM=$(sha256sum "$DESTINATION" | cut -f 1 -d ' ')
		if [ "$EXISTING_CHECKSUM" = "$CHECKSUM" ]; then return; fi
	fi

	local TMPFILE
	TMPFILE=$(mktemp "$PACKAGE_TMPDIR/download.$PACKAGE_NAME.XXXXXXXXX")
	echo "Downloading ${URL}"
	local TRYMAX=6
	for try in $(seq 1 $TRYMAX); do
		if curl -L --fail --retry 2 -o "$TMPFILE" "$URL"; then
			local ACTUAL_CHECKSUM
			ACTUAL_CHECKSUM=$(sha256sum "$TMPFILE" | cut -f 1 -d ' ')
			if [ "$CHECKSUM" != "SKIP_CHECKSUM" ]; then
				if [ "$CHECKSUM" != "$ACTUAL_CHECKSUM" ]; then
					>&2 printf "Wrong checksum for %s:\nExpected: %s\nActual:   %s\n" \
					           "$URL" "$CHECKSUM" "$ACTUAL_CHECKSUM"
					exit 1
				fi
			else
				printf "WARNING: No checksum check for %s:\nActual: %s\n" \
				       "$URL" "$ACTUAL_CHECKSUM"
			fi
			mv "$TMPFILE" "$DESTINATION"
			return
		else
			echo "Download of $URL failed (attempt $try/$TRYMAX)" 1>&2
			sleep 45
		fi
	done

	error_exit "Failed to download $URL"
}

# Download and extract sources.
builder_step_extract_package() {
	if [ -z "${PACKAGE_SRCURL:=""}" ] || [ -n "${PACKAGE_SKIP_SRC_EXTRACT:=""}" ]; then
		mkdir -p "$PACKAGE_SRCDIR"
		return
	fi
	cd "$PACKAGE_TMPDIR"
	local PKG_SRCURL=("${PACKAGE_SRCURL[@]}")
	local PKG_SHA256=("${PACKAGE_SHA256[@]}")
	if  [ "${#PKG_SRCURL[@]}" != "${#PKG_SHA256[@]}" ] && [ "${#PKG_SHA256[@]}" != "0" ]; then
		error_exit "Error: length of PACKAGE_SRCURL isn't equal to length of PACKAGE_SHA256."
	fi
	# STRIP=1 extracts archives straight into PACKAGE_SRCDIR while STRIP=0 puts them in subfolders. zip has same behaviour per default
	# If this isn't desired then this can be fixed in builder_step_post_extract_package.
	local STRIP=1
	for i in $(seq 0 $(( ${#PKG_SRCURL[@]}-1 ))); do
		test "$i" -gt 0 && STRIP=0
		local filename
		filename=$(basename "${PKG_SRCURL[$i]}")
		local file="$PACKAGE_CACHEDIR/$filename"
		# Allow PACKAGE_SHA256 to be empty:
		set +u
		url_download "${PKG_SRCURL[$i]}" "$file" "${PKG_SHA256[$i]}"
		set -u

		local folder
		set +o pipefail
		if [ "${file##*.}" = zip ]; then
			folder=$(unzip -qql "$file" | head -n1 | tr -s ' ' | cut -d' ' -f5-)
			rm -rf "$folder"
			unzip -q "$file"
			mv "$folder" "$PACKAGE_SRCDIR"
		else
			mkdir -p "$PACKAGE_SRCDIR"
			tar xf "$file" -C "$PACKAGE_SRCDIR" --strip-components=$STRIP
		fi
		set -o pipefail
	done
}

# Steps to do right after extracting package sources.
builder_step_post_extract_package() {
        return
}

# Apply patches and replace config.sub/config.guess scripts.
builder_step_patch_package() {
	local DEBUG_PATCHES=""

	if [ "$CONFIG_BUILDER_DEBUG" = "true" ]; then
		DEBUG_PATCHES=$(find "$PACKAGE_BUILDER_DIR" -mindepth 1 -maxdepth 1 -name "*.patch.debug")
	fi

	# Suffix patch with ".patch32" or ".patch64" to only apply for these bitnesses:
	shopt -s nullglob
	for patch in "$PACKAGE_BUILDER_DIR"/*.patch{$PACKAGE_TARGET_ARCH_BITS,} $DEBUG_PATCHES; do
		if [ -f "$patch" ]; then
			echo "Applying patch: ${patch}"
			patch -p1 -i "$patch"
		fi
	done
	shopt -u nullglob

	# Replace autotools build-aux/config.{sub,guess} with ours to add android targets.
	find . -name config.sub -exec chmod u+w '{}' \; -exec cp "$BUILDER_SCRIPTDIR/scripts/config.sub" '{}' \;
	find . -name config.guess -exec chmod u+w '{}' \; -exec cp "$BUILDER_SCRIPTDIR/scripts/config.guess" '{}' \;
}

# Steps to do before configuring the package.
builder_step_pre_configure() {
	return
}

# Utility function to setup CMake installation.
builder_setup_cmake() {
	local BUILDER_CMAKE_MAJORVESION=3.16
	local BUILDER_CMAKE_MINORVERSION=1
	local BUILDER_CMAKE_VERSION="$BUILDER_CMAKE_MAJORVESION.$BUILDER_CMAKE_MINORVERSION"
	local BUILDER_CMAKE_TARNAME="cmake-${BUILDER_CMAKE_VERSION}-Linux-x86_64.tar.gz"
	local BUILDER_CMAKE_TARFILE="$PACKAGE_TMPDIR/$BUILDER_CMAKE_TARNAME"
	local BUILDER_CMAKE_FOLDER="$BUILDER_TOPDIR/.cmake-$BUILDER_CMAKE_VERSION"

	if [ ! -d "$BUILDER_CMAKE_FOLDER" ]; then
		url_download https://cmake.org/files/v$BUILDER_CMAKE_MAJORVESION/$BUILDER_CMAKE_TARNAME \
			"$BUILDER_CMAKE_TARFILE" \
			ff84a47d0815778e23ff08a7c705ebabdc10687889df7b7ec5ecdc8c61af7ab7
		rm -Rf "$PACKAGE_TMPDIR/cmake-${BUILDER_CMAKE_VERSION}-Linux-x86_64"
		tar xf "$BUILDER_CMAKE_TARFILE" -C "$PACKAGE_TMPDIR"
		mv "$PACKAGE_TMPDIR/cmake-${BUILDER_CMAKE_VERSION}-Linux-x86_64" \
			"$BUILDER_CMAKE_FOLDER"
	fi

	export PATH="$BUILDER_CMAKE_FOLDER/bin:$PATH"
	export CMAKE_INSTALL_ALWAYS=1
}

builder_setup_meson() {
	local BUILDER_MESON_VERSION=0.61.2
	local BUILDER_MESON_FOLDER="${BUILDER_TOPDIR}/.meson-${BUILDER_MESON_VERSION}"

	if [ ! -d "$BUILDER_MESON_FOLDER" ]; then
		local MESON_TAR_NAME="meson-${BUILDER_MESON_VERSION}.tar.gz"
		local MESON_TAR_FILE="${PACKAGE_TMPDIR}/${MESON_TAR_NAME}"
		local MESON_TMP_FOLDER="${PACKAGE_TMPDIR}/meson-${BUILDER_MESON_VERSION}"

		url_download \
			"https://github.com/mesonbuild/meson/releases/download/$BUILDER_MESON_VERSION/meson-${BUILDER_MESON_VERSION}.tar.gz" \
			"$MESON_TAR_FILE" \
			0233a7f8d959079318f6052b0939c27f68a5de86ba601f25c9ee6869fb5f5889

		tar xf "$MESON_TAR_FILE" -C "$PACKAGE_TMPDIR"

		if [ "$BUILDER_MESON_VERSION" = "0.61.2" ]; then
			local MESON_0_61_2_GTKDOC_PATCH_FILE="${PACKAGE_TMPDIR}/meson-0.61.2-gtkdoc.patch"

			url_download \
				"https://github.com/mesonbuild/meson/commit/266e8acb5807b38a550cb5145cea0e19545a21d7.patch" \
				"$MESON_0_61_2_GTKDOC_PATCH_FILE" \
				a25668672206991d15b18fa952859503672ac2814ae02605e0c7c90d7f918030

			patch --silent -p1 -d "$MESON_TMP_FOLDER" < "$MESON_0_61_2_GTKDOC_PATCH_FILE"
		fi

		# Patch meson to always return true for libintl check.
		patch --silent -p1 -d "$MESON_TMP_FOLDER" < "$BUILDER_SCRIPTDIR"/scripts/patches/meson_libintl.patch || {
			error_exit "[${FUNCNAME[0]}]: Meson libintl patch failed."
		}

		mv "$MESON_TMP_FOLDER" "$BUILDER_MESON_FOLDER"
	fi

	BUILDER_MESON="${BUILDER_MESON_FOLDER}/meson.py"
	BUILDER_MESON_CROSSFILE="${PACKAGE_TMPDIR}/meson-crossfile-${PACKAGE_TARGET_ARCH}.txt"
	local MESON_CPU MESON_CPU_FAMILY
	if [ "$PACKAGE_TARGET_ARCH" = "arm" ]; then
		MESON_CPU_FAMILY="arm"
		MESON_CPU="armv7"
	elif [ "$PACKAGE_TARGET_ARCH" = "i686" ]; then
		MESON_CPU_FAMILY="x86"
		MESON_CPU="i686"
	elif [ "$PACKAGE_TARGET_ARCH" = "x86_64" ]; then
		MESON_CPU_FAMILY="x86_64"
		MESON_CPU="x86_64"
	elif [ "$PACKAGE_TARGET_ARCH" = "aarch64" ]; then
		MESON_CPU_FAMILY="aarch64"
		MESON_CPU="aarch64"
	else
		error_exit "Unsupported arch: $PACKAGE_TARGET_ARCH"
	fi

	local CONTENT=""
	echo "[binaries]" > $BUILDER_MESON_CROSSFILE
	echo "ar = '$AR'" >> $BUILDER_MESON_CROSSFILE
	echo "c = '$CC'" >> $BUILDER_MESON_CROSSFILE
	echo "cmake = 'cmake'" >> $BUILDER_MESON_CROSSFILE
	echo "cpp = '$CXX'" >> $BUILDER_MESON_CROSSFILE
	echo "ld = '$LD'" >> $BUILDER_MESON_CROSSFILE
	echo "pkgconfig = '$PKG_CONFIG'" >> $BUILDER_MESON_CROSSFILE
	echo "strip = '$STRIP'" >> $BUILDER_MESON_CROSSFILE

	echo '' >> $BUILDER_MESON_CROSSFILE
	echo "[properties]" >> $BUILDER_MESON_CROSSFILE
	echo "needs_exe_wrapper = true" >> $BUILDER_MESON_CROSSFILE

	echo '' >> $BUILDER_MESON_CROSSFILE
	echo "[built-in options]" >> $BUILDER_MESON_CROSSFILE

	echo -n "c_args = [" >> $BUILDER_MESON_CROSSFILE
	local word first=true
	for word in $CFLAGS $CPPFLAGS; do
		if [ "$first" = "true" ]; then
			first=false
		else
			echo -n ", " >> $BUILDER_MESON_CROSSFILE
		fi
		echo -n "'$word'" >> $BUILDER_MESON_CROSSFILE
	done
	echo ']' >> $BUILDER_MESON_CROSSFILE

	echo -n "cpp_args = [" >> $BUILDER_MESON_CROSSFILE
	local word first=true
	for word in $CXXFLAGS $CPPFLAGS; do
		if [ "$first" = "true" ]; then
			first=false
		else
			echo -n ", " >> $BUILDER_MESON_CROSSFILE
		fi
		echo -n "'$word'" >> $BUILDER_MESON_CROSSFILE
	done
	echo ']' >> $BUILDER_MESON_CROSSFILE

	local property
	for property in c_link_args cpp_link_args; do
		echo -n "$property = [" >> $BUILDER_MESON_CROSSFILE
		first=true
		for word in $LDFLAGS; do
			if [ "$first" = "true" ]; then
				first=false
			else
				echo -n ", " >> $BUILDER_MESON_CROSSFILE
			fi
			echo -n "'$word'" >> $BUILDER_MESON_CROSSFILE
		done
		echo ']' >> $BUILDER_MESON_CROSSFILE
	done

	echo '' >> $BUILDER_MESON_CROSSFILE
	echo "[host_machine]" >> $BUILDER_MESON_CROSSFILE
	echo "cpu_family = '$MESON_CPU_FAMILY'" >> $BUILDER_MESON_CROSSFILE
	echo "cpu = '$MESON_CPU'" >> $BUILDER_MESON_CROSSFILE
	echo "endian = 'little'" >> $BUILDER_MESON_CROSSFILE
	echo "system = 'android'" >> $BUILDER_MESON_CROSSFILE
}

# Configure the package.
builder_step_configure() {
	if [ -f "$PACKAGE_SRCDIR/CMakeLists.txt" ]; then
		builder_setup_cmake

		local BUILD_TYPE=Release
		if [ "$CONFIG_BUILDER_DEBUG" = "true" ]; then
			BUILD_TYPE=Debug
		fi

		local CMAKE_PROC=$PACKAGE_TARGET_ARCH
		if [ "$CMAKE_PROC" = "arm" ]; then
			CMAKE_PROC="armv7-a"
		fi

		if [ "$PACKAGE_TARGET_ARCH" = "arm" ]; then
			PACKAGE_TARGET_PLATFORM="armv7a-linux-androideabi${PACKAGE_API_LEVEL}"
		else
			PACKAGE_TARGET_PLATFORM="${PACKAGE_TARGET_PLATFORM}${PACKAGE_API_LEVEL}"
		fi

		CFLAGS+=" -fno-addrsig --target=$PACKAGE_TARGET_PLATFORM -D__ANDROID_API__=$PACKAGE_API_LEVEL"
		CXXFLAGS+=" -fno-addrsig --target=$PACKAGE_TARGET_PLATFORM -D__ANDROID_API__=$PACKAGE_API_LEVEL"
		LDFLAGS+=" --target=$PACKAGE_TARGET_PLATFORM"

		cmake "$PACKAGE_SRCDIR" \
			-DCMAKE_AR="$(command -v $AR)" \
			-DCMAKE_UNAME="$(command -v uname)" \
			-DCMAKE_RANLIB="$(command -v $RANLIB)" \
			-DCMAKE_BUILD_TYPE=$BUILD_TYPE \
			-DCMAKE_C_FLAGS="$CFLAGS $CPPFLAGS" \
			-DCMAKE_CXX_FLAGS="$CXXFLAGS $CPPFLAGS" \
			-DCMAKE_FIND_ROOT_PATH=$PACKAGE_INSTALL_PREFIX \
			-DCMAKE_FIND_ROOT_PATH_MODE_PROGRAM=NEVER \
			-DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY \
			-DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY \
			-DCMAKE_INSTALL_PREFIX=$PACKAGE_INSTALL_PREFIX \
			-DCMAKE_MAKE_PROGRAM=$(command -v make) \
			-DCMAKE_SKIP_INSTALL_RPATH=ON \
			-DCMAKE_USE_SYSTEM_LIBRARIES=True \
			-DDOXYGEN_EXECUTABLE= \
			-DBUILD_TESTING=OFF \
			-DCMAKE_CROSSCOMPILING=True \
			-DCMAKE_LINKER="$CROSS_TOOLCHAIN_DIR/bin/$LD $LDFLAGS" \
			-DCMAKE_SYSTEM_NAME=Android \
			-DCMAKE_SYSTEM_VERSION=$PACKAGE_API_LEVEL \
			-DCMAKE_SYSTEM_PROCESSOR=$CMAKE_PROC \
			-DCMAKE_ANDROID_STANDALONE_TOOLCHAIN=$CROSS_TOOLCHAIN_DIR \
			$PACKAGE_EXTRA_CONFIGURE_ARGS

			return
	elif [ -f "${PACKAGE_SRCDIR}/meson.build" ]; then
		builder_setup_meson
		CC=gcc CXX=g++ CFLAGS= CXXFLAGS= CPPFLAGS= LDFLAGS= "$BUILDER_MESON" \
			"$PACKAGE_SRCDIR" \
			"$PACKAGE_BUILDDIR" \
				--cross-file "$BUILDER_MESON_CROSSFILE" \
				--prefix "$PACKAGE_INSTALL_PREFIX" \
				--libdir lib \
				--buildtype minsize \
				--strip \
				$PACKAGE_EXTRA_CONFIGURE_ARGS
	fi

	if [ ! -e "$PACKAGE_SRCDIR/configure" ]; then
		return
	fi

	local DISABLE_NLS="--disable-nls"
	if [ "$PACKAGE_EXTRA_CONFIGURE_ARGS" != "${PACKAGE_EXTRA_CONFIGURE_ARGS/--enable-nls/}" ]; then
		# Do not --disable-nls if package explicitly enables it (for gettext itself)
		DISABLE_NLS=""
	fi

	local HOST_FLAG="--host=$PACKAGE_TARGET_PLATFORM"
	if [ "$PACKAGE_EXTRA_CONFIGURE_ARGS" != "${PACKAGE_EXTRA_CONFIGURE_ARGS/--host=/}" ]; then
		HOST_FLAG=""
	fi

	local LIBEXEC_FLAG="--libexecdir=$PACKAGE_INSTALL_PREFIX/libexec"
	if [ "$PACKAGE_EXTRA_CONFIGURE_ARGS" != "${PACKAGE_EXTRA_CONFIGURE_ARGS/--libexecdir=/}" ]; then
		LIBEXEC_FLAG=""
	fi

	# Some packages provides a $PKG-config script which some configure scripts pickup instead of pkg-config:
	mkdir "$PACKAGE_TMPDIR/config-scripts"
	for f in "$PACKAGE_INSTALL_PREFIX"/bin/*config; do
		test -f "$f" && cp "$f" "$PACKAGE_TMPDIR/config-scripts"
	done
	export PATH=$PACKAGE_TMPDIR/config-scripts:$PATH

	# Avoid gnulib wrapping of functions when cross compiling. See
	# http://wiki.osdev.org/Cross-Porting_Software#Gnulib
	# https://gitlab.com/sortix/sortix/wikis/Gnulib
	local AVOID_GNULIB=""
	AVOID_GNULIB+=" ac_cv_func_calloc_0_nonnull=yes"
	AVOID_GNULIB+=" ac_cv_func_chown_works=yes"
	AVOID_GNULIB+=" ac_cv_func_getgroups_works=yes"
	AVOID_GNULIB+=" ac_cv_func_malloc_0_nonnull=yes"
	AVOID_GNULIB+=" ac_cv_func_realloc_0_nonnull=yes"
	AVOID_GNULIB+=" am_cv_func_working_getline=yes"
	AVOID_GNULIB+=" gl_cv_func_dup2_works=yes"
	AVOID_GNULIB+=" gl_cv_func_fcntl_f_dupfd_cloexec=yes"
	AVOID_GNULIB+=" gl_cv_func_fcntl_f_dupfd_works=yes"
	AVOID_GNULIB+=" gl_cv_func_fnmatch_posix=yes"
	AVOID_GNULIB+=" gl_cv_func_getcwd_abort_bug=no"
	AVOID_GNULIB+=" gl_cv_func_getcwd_null=yes"
	AVOID_GNULIB+=" gl_cv_func_getcwd_path_max=yes"
	AVOID_GNULIB+=" gl_cv_func_getcwd_posix_signature=yes"
	AVOID_GNULIB+=" gl_cv_func_gettimeofday_clobber=no"
	AVOID_GNULIB+=" gl_cv_func_gettimeofday_posix_signature=yes"
	AVOID_GNULIB+=" gl_cv_func_link_works=yes"
	AVOID_GNULIB+=" gl_cv_func_lstat_dereferences_slashed_symlink=yes"
	AVOID_GNULIB+=" gl_cv_func_malloc_0_nonnull=yes"
	AVOID_GNULIB+=" gl_cv_func_memchr_works=yes"
	AVOID_GNULIB+=" gl_cv_func_mkdir_trailing_dot_works=yes"
	AVOID_GNULIB+=" gl_cv_func_mkdir_trailing_slash_works=yes"
	AVOID_GNULIB+=" gl_cv_func_mkfifo_works=yes"
	AVOID_GNULIB+=" gl_cv_func_mknod_works=yes"
	AVOID_GNULIB+=" gl_cv_func_realpath_works=yes"
	AVOID_GNULIB+=" gl_cv_func_select_detects_ebadf=yes"
	AVOID_GNULIB+=" gl_cv_func_snprintf_posix=yes"
	AVOID_GNULIB+=" gl_cv_func_snprintf_retval_c99=yes"
	AVOID_GNULIB+=" gl_cv_func_snprintf_truncation_c99=yes"
	AVOID_GNULIB+=" gl_cv_func_stat_dir_slash=yes"
	AVOID_GNULIB+=" gl_cv_func_stat_file_slash=yes"
	AVOID_GNULIB+=" gl_cv_func_strerror_0_works=yes"
	AVOID_GNULIB+=" gl_cv_func_symlink_works=yes"
	AVOID_GNULIB+=" gl_cv_func_tzset_clobber=no"
	AVOID_GNULIB+=" gl_cv_func_unlink_honors_slashes=yes"
	AVOID_GNULIB+=" gl_cv_func_unlink_honors_slashes=yes"
	AVOID_GNULIB+=" gl_cv_func_vsnprintf_posix=yes"
	AVOID_GNULIB+=" gl_cv_func_vsnprintf_zerosize_c99=yes"
	AVOID_GNULIB+=" gl_cv_func_wcwidth_works=yes"
	AVOID_GNULIB+=" gl_cv_func_working_getdelim=yes"
	AVOID_GNULIB+=" gl_cv_func_working_mkstemp=yes"
	AVOID_GNULIB+=" gl_cv_func_working_mktime=yes"
	AVOID_GNULIB+=" gl_cv_func_working_strerror=yes"
	AVOID_GNULIB+=" gl_cv_header_working_fcntl_h=yes"
	AVOID_GNULIB+=" gl_cv_C_locale_sans_EILSEQ=yes"

	# NOTE: We do not want to quote AVOID_GNULIB as we want word expansion.
	# shellcheck disable=SC2086
	env $AVOID_GNULIB "$PACKAGE_SRCDIR/configure" \
		--disable-dependency-tracking \
		--prefix=$PACKAGE_INSTALL_PREFIX \
		--libdir=$PACKAGE_INSTALL_PREFIX/lib \
		--disable-rpath --disable-rpath-hack \
		--disable-shared --enable-static \
		$HOST_FLAG \
		$PACKAGE_EXTRA_CONFIGURE_ARGS \
		$DISABLE_NLS \
		$LIBEXEC_FLAG
}

# Additional steps to do right after configuration.
builder_step_post_configure() {
	return
}

# Build the package.
builder_step_make() {
	if [ -f "Makefile" ] || [ -f "makefile" ] || \
		[ -f "GNUmakefile" ] || [ -n "$PACKAGE_EXTRA_MAKE_ARGS" ]; then
		if [ -z "$PACKAGE_EXTRA_MAKE_ARGS" ]; then
			make -j "$CONFIG_BUILDER_MAKE_PROCESSES"
		else
			make -j "$CONFIG_BUILDER_MAKE_PROCESSES" $PACKAGE_EXTRA_MAKE_ARGS
		fi
	elif [ -f build.ninja ]; then
		ninja -w dupbuild=warn -j $CONFIG_BUILDER_MAKE_PROCESSES
	fi
}

# Install built artifacts.
builder_step_make_install() {
	: "${PACKAGE_MAKE_INSTALL_TARGET:="install"}"
	if [ -f "Makefile" ] || [ -f "makefile" ] || \
		[ -f "GNUmakefile" ] || [ -n "$PACKAGE_EXTRA_MAKE_ARGS" ]; then
		# Some packages have problem with parallell install, and it does not buy much, so use -j 1.
		if [ -z "$PACKAGE_EXTRA_MAKE_ARGS" ]; then
			make -j 1 $PACKAGE_MAKE_INSTALL_TARGET
		else
			make -j 1 $PACKAGE_EXTRA_MAKE_ARGS $PACKAGE_MAKE_INSTALL_TARGET
		fi
	elif [ -f build.ninja ]; then
		ninja -w dupbuild=warn -j 1 $PACKAGE_MAKE_INSTALL_TARGET
	fi
}

# Additional steps to do right after installation.
builder_step_post_make_install() {
	return
}

_show_usage() {
	echo
	echo "Usage: ./build-package.sh [OPTIONS] pkg1 ..."
	echo
	echo "Compile the specified packages."
	echo
	echo "Options:"
	echo
	echo "  -a [target architecture]"
	echo
	echo "     The architecture to build for. Can be a one of"
	echo "     aarch64 (default), arm, i686, x86_64 or all."
	echo
	echo "  -d"
	echo
	echo "     Build package optimized for debugging purposes."
	echo
	echo "  -f"
	echo
	echo "     Force build even if package has already been built."
	echo
	echo "  -s"
	echo
	echo "     Skip dependency check."
	echo
	exit 1
}

while getopts :a:hdfqs option; do
	case "$option" in
		a) PACKAGE_TARGET_ARCH="$OPTARG";;
		h) _show_usage;;
		d) CONFIG_BUILDER_DEBUG=true;;
		f) CONFIG_BUILDER_FORCE=true;;
		s) CONFIG_BUILDER_SKIP_DEPCHECK=true;;
		?) error_exit "./build-package.sh: illegal option -$OPTARG";;
	esac
done
shift $((OPTIND-1))

if [ $# -lt 1 ]; then
	_show_usage
fi
unset -f _show_usage

# Initialize directory where build cache will be stored.
mkdir -p "$BUILDER_TOPDIR"

# Handle 'all' arch:
if [ -n "${PACKAGE_TARGET_ARCH+x}" ] && [ "${PACKAGE_TARGET_ARCH}" = 'all' ]; then
	cmdline=("$@")

	if [ "$CONFIG_BUILDER_FORCE" = "true" ]; then
		cmdline=("-f" "${cmdline[@]}")
	fi

	if [ "$CONFIG_BUILDER_DEBUG" = "true" ]; then
		cmdline=("-d" "${cmdline[@]}")
	fi

	for arch in 'aarch64' 'arm' 'i686' 'x86_64'; do
		./build-package.sh -a $arch "${cmdline[@]}"
	done
	exit
fi

while (($# > 0)); do
	(
		# Check the package to build:
		PACKAGE_NAME=$(basename "$1")
		PACKAGE_BUILDER_DIR="$BUILDER_SCRIPTDIR/packages/$PACKAGE_NAME"
		PACKAGE_BUILDER_SCRIPT="$PACKAGE_BUILDER_DIR/build.sh"

		if [ ! -f "$PACKAGE_BUILDER_SCRIPT" ]; then
			error_exit "No build.sh script at package dir $PACKAGE_BUILDER_DIR!"
		fi

		PACKAGE_BUILDDIR="$BUILDER_TOPDIR/$PACKAGE_NAME/build"
		PACKAGE_CACHEDIR="$BUILDER_TOPDIR/$PACKAGE_NAME/cache"
		PACKAGE_PACKAGEDIR="$BUILDER_TOPDIR/$PACKAGE_NAME/package"
		PACKAGE_SRCDIR="$BUILDER_TOPDIR/$PACKAGE_NAME/src"
		PACKAGE_SHA256=""
		PACKAGE_TMPDIR="$BUILDER_TOPDIR/$PACKAGE_NAME/tmp"
		PACKAGE_EXTRA_CONFIGURE_ARGS=""
		PACKAGE_EXTRA_MAKE_ARGS=""
		PACKAGE_BUILD_IN_SRC=""
		PACKAGE_DEPENDS=""

		unset CFLAGS CPPFLAGS LDFLAGS CXXFLAGS

		if [ -f "$BUILDER_TOPDIR/.current_arch" ]; then
			previous_arch=$(cat "$BUILDER_TOPDIR/.current_arch")

			if [ -z "$previous_arch" ]; then
				error_exit "$BUILDER_TOPDIR/.current_arch cannot be empty."
			fi

			if [ ! -d "$BUILDER_TOPDIR/.buildroot_backups" ]; then
				mkdir -p "$BUILDER_TOPDIR/.buildroot_backups"
			fi

			if [ "$PACKAGE_TARGET_ARCH" != "$previous_arch" ]; then
				buildroot_backups="$BUILDER_TOPDIR/.buildroot_backups"

				# Save current /data (removing old backup if any)
				rm -rf "${buildroot_backups:?}/${previous_arch:?}"
				if [ -d "/data/data" ]; then
					mv "/data/data" "$buildroot_backups/$previous_arch"
				fi

				# Restore new one (if any)
				if [ -d "$buildroot_backups/$PACKAGE_TARGET_ARCH" ]; then
					mv "$buildroot_backups/$PACKAGE_TARGET_ARCH" "/data/data"
				fi
				unset buildroot_backups
			fi

			unset previous_arch
		fi

		# Keep track of current arch we are building for.
		echo "$PACKAGE_TARGET_ARCH" > "$BUILDER_TOPDIR/.current_arch"

		source "$PACKAGE_BUILDER_SCRIPT"

		if [ "$CONFIG_BUILDER_SKIP_DEPCHECK" != "true" ]; then
			while read -r dep; do
				echo "Building dependency $dep if necessary..."
				"$BUILDER_SCRIPTDIR/build-package.sh" -a "$PACKAGE_TARGET_ARCH" -s "$dep"
			done < <("$BUILDER_SCRIPTDIR/scripts/buildorder.py" "$PACKAGE_BUILDER_DIR")
		fi

		if [ "$CONFIG_BUILDER_FORCE" != "true" ] && [ -e "/data/data/.built-packages/$PACKAGE_NAME" ]; then
			if [ "$(cat "/data/data/.built-packages/$PACKAGE_NAME")" = "$PACKAGE_VERSION" ]; then
				echo "${PACKAGE_NAME}@${PACKAGE_VERSION} built - skipping"
				exit 0
			fi
		fi

		# Clean old state.
		rm -rf "$PACKAGE_BUILDDIR" \
			"$PACKAGE_PACKAGEDIR" \
			"$PACKAGE_SRCDIR" \
			"$PACKAGE_TMPDIR"
		mkdir -p "$PACKAGE_PACKAGEDIR" \
			"$PACKAGE_TMPDIR" \
			"$PACKAGE_CACHEDIR"

		if [ -n "$PACKAGE_BUILD_IN_SRC" ]; then
			ln -sfr "$PACKAGE_SRCDIR" "$PACKAGE_BUILDDIR"
			PACKAGE_BUILDDIR="$PACKAGE_SRCDIR"
		else
			mkdir -p "$PACKAGE_BUILDDIR"
		fi

		echo "building $PACKAGE_NAME for arch $PACKAGE_TARGET_ARCH..."
		test -t 1 && printf "\033]0;%s...\007" "$PACKAGE_NAME" || true

		if [ "x86_64" = "$PACKAGE_TARGET_ARCH" ] || [ "aarch64" = "$PACKAGE_TARGET_ARCH" ]; then
			PACKAGE_TARGET_ARCH_BITS=64
		else
			PACKAGE_TARGET_ARCH_BITS=32
		fi

		if [ "$PACKAGE_TARGET_ARCH" = "arm" ]; then
			PACKAGE_TARGET_PLATFORM="${PACKAGE_TARGET_ARCH}-linux-androideabi"
			export CC="armv7a-linux-androideabi${PACKAGE_API_LEVEL}-clang"
			export CPP="armv7a-linux-androideabi${PACKAGE_API_LEVEL}-cpp"
		else
			PACKAGE_TARGET_PLATFORM="${PACKAGE_TARGET_ARCH}-linux-android"
			export CC="${PACKAGE_TARGET_PLATFORM}${PACKAGE_API_LEVEL}-clang"
			export CPP="${PACKAGE_TARGET_PLATFORM}${PACKAGE_API_LEVEL}-cpp"
		fi

		export AR="llvm-ar"
		export AS="$CC"
		export CXX="${CC}++"
		export CC_FOR_BUILD="gcc"
		export LD="ld.lld"
		export OBJDUMP="llvm-objdump"
		export PKG_CONFIG="${CROSS_TOOLCHAIN_DIR}/bin/${PACKAGE_TARGET_PLATFORM}-pkg-config"
		export RANLIB="llvm-ranlib"
		export READELF="llvm-readelf"
		export STRIP="llvm-strip"

		export CFLAGS="-fstack-protector-strong"
		export CPPFLAGS="-I${PACKAGE_INSTALL_PREFIX}/include -D_FORTIFY_SOURCE=2 -DAPPLICATION_RUNTIME_PREFIX=${PACKAGE_INSTALL_PREFIX}"
		export LDFLAGS="-L${PACKAGE_INSTALL_PREFIX}/lib"

		if [ "$CONFIG_BUILDER_DEBUG" = "true" ]; then
			CFLAGS+=" -g3 -O1"
		else
			CFLAGS+=" -O2 -ftree-vectorize"
		fi

		if [ "$PACKAGE_TARGET_ARCH" = "arm" ]; then
			# https://developer.android.com/ndk/guides/standalone_toolchain.html#abi_compatibility:
			CFLAGS+=" -march=armv7-a -mfpu=vfp -mfloat-abi=softfp -mthumb"
			LDFLAGS+=" -march=armv7-a"
		elif [ "$PACKAGE_TARGET_ARCH" = "i686" ]; then
			# From $NDK/docs/CPU-ARCH-ABIS.html:
			CFLAGS+=" -march=i686 -msse3 -mstackrealign -mfpmath=sse"
		elif [ "$PACKAGE_TARGET_ARCH" = "aarch64" ]; then
			:
		elif [ "$PACKAGE_TARGET_ARCH" = "x86_64" ]; then
			:
		else
			error_exit "Invalid arch '$PACKAGE_TARGET_ARCH' - support arches are 'arm', 'i686', 'aarch64', 'x86_64'"
		fi

		export CXXFLAGS="$CFLAGS"

		# If libandroid-support is declared as a dependency, link to it explicitly:
		if [ "$PACKAGE_DEPENDS" != "${PACKAGE_DEPENDS/libandroid-support/}" ]; then
			LDFLAGS+=" -landroid-support"
		fi

		export ac_cv_func_getpwent=no
		export ac_cv_func_getpwnam=no
		export ac_cv_func_getpwuid=no
		export ac_cv_func_sigsetmask=no
		export ac_cv_c_bigendian=no

		# Create a pkg-config wrapper. We use path to host pkg-config to
		# avoid picking up a cross-compiled pkg-config later on.
		export PKG_CONFIG_LIBDIR="$PACKAGE_INSTALL_PREFIX/lib/pkgconfig"
		mkdir -p "$PKG_CONFIG_LIBDIR"
		cat > "$PKG_CONFIG" <<-HERE
			#!/bin/sh
			export PKG_CONFIG_DIR=
			export PKG_CONFIG_LIBDIR="$PKG_CONFIG_LIBDIR"
			exec $(command -v pkg-config) "\$@"
		HERE
		chmod +x "$PKG_CONFIG"

		builder_step_extract_package

		cd "$PACKAGE_SRCDIR"
		builder_step_post_extract_package

		cd "$PACKAGE_SRCDIR"
		builder_step_patch_package

		cd "$PACKAGE_SRCDIR"
		builder_step_pre_configure

		cd "$PACKAGE_BUILDDIR"
		builder_step_configure

		cd "$PACKAGE_BUILDDIR"
		builder_step_post_configure

		cd "$PACKAGE_BUILDDIR"
		builder_step_make

		cd "$PACKAGE_BUILDDIR"
		builder_step_make_install

		cd "$PACKAGE_BUILDDIR"
		builder_step_post_make_install

		mkdir -p "/data/data/.built-packages"
		echo "$PACKAGE_VERSION" > "/data/data/.built-packages/$PACKAGE_NAME"

		echo "finished building of '$PACKAGE_NAME'"
		test -t 1 && printf "\033]0;%s - DONE\007" "$PACKAGE_NAME" || true
	)

	shift 1
done
