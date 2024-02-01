PACKAGE_VERSION="1.3.1"
PACKAGE_SRCURL="https://www.zlib.net/zlib-$PACKAGE_VERSION.tar.xz"
PACKAGE_SHA256="38ef96b8dfe510d42707d9c781877914792541133e1870841463bfa73f883e32"

builder_step_configure() {
	if [ "$PACKAGE_TARGET_ARCH" = "aarch64" ]; then
		CFLAGS+=" -march=armv8-a+crc"
	fi

	CFLAGS+=" $CPPFLAGS -fPIC"
	LDFLAGS+=" -fPIC"
	"$PACKAGE_SRCDIR"/configure \
		--prefix="$PACKAGE_INSTALL_PREFIX" --static
}
