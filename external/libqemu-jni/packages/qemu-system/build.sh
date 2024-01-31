PACKAGE_VERSION="7.0.0"
PACKAGE_SRCURL="https://download.qemu.org/qemu-${PACKAGE_VERSION}.tar.xz"
PACKAGE_SHA256="f6b375c7951f728402798b0baabb2d86478ca53d44cedbefabbe1c46bf46f839"
PACKAGE_DEPENDS="glib, libiconv, libslirp, pixman, zlib"
PACKAGE_BUILD_IN_SRC="true"

builder_step_configure() {
	CFLAGS+=" $CPPFLAGS"
	CXXFLAGS+=" $CPPFLAGS"

	if [ "$PACKAGE_TARGET_ARCH" = "aarch64" ]; then
		rm -rf "${PACKAGE_BUILDDIR}"/_lib
		mkdir -p "${PACKAGE_BUILDDIR}"/_lib

		cd "$PACKAGE_BUILDDIR"
		mkdir -p _setjmp-aarch64
		pushd _setjmp-aarch64
		mkdir -p private

		local s
		for s in "${PACKAGE_BUILDER_DIR}"/setjmp-aarch64/{setjmp.S,private-*.h}; do
			local f=$(basename "${s}")
			cp "${s}" ./"${f/-//}"
		done
		$CC $CFLAGS $CPPFLAGS -I. setjmp.S -c
		$AR cru "${PACKAGE_BUILDDIR}"/_lib/libandroid-setjmp.a setjmp.o
		popd

		LDFLAGS+=" -L${PACKAGE_BUILDDIR}/_lib -l:libandroid-setjmp.a"
	fi

	if [ "$PACKAGE_TARGET_ARCH" = "i686" ]; then
		LDFLAGS+=" -latomic"
	fi

	# Note: using --disable-stack-protector since stack protector
	# flags already passed by build scripts but we do not want to
	# override them with what QEMU configure provides.
	./configure \
		--prefix="$PACKAGE_INSTALL_PREFIX" \
		--cross-prefix="${PACKAGE_TARGET_PLATFORM}-" \
		--host-cc="gcc" \
		--cc="$CC" \
		--cxx="$CXX" \
		--objcc="$CC" \
		--disable-stack-protector \
		--smbd="/system/bin/smbd" \
		--enable-coroutine-pool \
		--enable-trace-backends=nop \
		--disable-guest-agent \
		--disable-gnutls \
		--disable-nettle \
		--disable-gcrypt \
		--disable-sdl \
		--disable-sdl-image \
		--disable-gtk \
		--disable-vte \
		--disable-curses \
		--disable-iconv \
		--disable-vnc \
		--disable-vnc-sasl \
		--disable-vnc-jpeg \
		--disable-vnc-png \
		--disable-xen \
		--disable-xen-pci-passthrough \
		--enable-virtfs \
		--disable-curl \
		--enable-fdt \
		--disable-kvm \
		--disable-hax \
		--disable-hvf \
		--disable-whpx \
		--disable-libnfs \
		--disable-libusb \
		--disable-lzo \
		--disable-snappy \
		--disable-bzip2 \
		--disable-lzfse \
		--disable-seccomp \
		--disable-libssh \
		--disable-bochs \
		--disable-cloop \
		--disable-dmg \
		--disable-parallels \
		--disable-qed \
		--disable-vhost-user \
		--disable-vhost-user-blk-server \
		--disable-tools \
		--target-list=x86_64-softmmu
}

builder_step_post_make_install() {
	local bindir

	case "$PACKAGE_TARGET_ARCH" in
		aarch64) bindir="arm64-v8a";;
		arm) bindir="armeabi-v7a";;
		i686) bindir="x86";;
		x86_64) bindir="x86_64";;
		*) echo "Invalid architecture '$PACKAGE_TARGET_ARCH'" && return 1;;
	esac

	install -Dm600 "$PACKAGE_INSTALL_PREFIX"/lib/libqemu-jni.so \
		"${BUILDER_SCRIPTDIR}/jniLibs/${bindir}/libqemu-jni.so"
	"$STRIP" -s "${BUILDER_SCRIPTDIR}/jniLibs/${bindir}/libqemu-jni.so"
}
