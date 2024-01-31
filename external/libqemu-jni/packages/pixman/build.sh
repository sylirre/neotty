PACKAGE_VERSION="0.40.0"
PACKAGE_SRCURL="https://cairographics.org/releases/pixman-${PACKAGE_VERSION}.tar.gz"
PACKAGE_SHA256="6d200dec3740d9ec4ec8d1180e25779c00bc749f94278c8b9021f5534db223fc"
PACKAGE_DEPENDS="libandroid-cpufeatures"

PACKAGE_EXTRA_CONFIGURE_ARGS="
--disable-loongson-mmi
--disable-vmx
--disable-arm-iwmmxt
--disable-arm-iwmmxt2
"

builder_step_pre_configure() {
	# Use autotools.
	rm -f meson.build

	export LIBS="-landroid-cpufeatures"

	if [ "$PACKAGE_TARGET_ARCH" = "arm" ]; then
		CFLAGS+=" -fno-integrated-as"
		PACKAGE_EXTRA_CONFIGURE_ARGS+=" --enable-arm-neon"
	elif [ "$PACKAGE_TARGET_ARCH" = "i686" ] || [ "$PACKAGE_TARGET_ARCH" = "x86_64" ]; then
		PACKAGE_EXTRA_CONFIGURE_ARGS+=" --enable-mmx"
		PACKAGE_EXTRA_CONFIGURE_ARGS+=" --enable-sse2"
		PACKAGE_EXTRA_CONFIGURE_ARGS+=" --enable-ssse3"
	fi
}
