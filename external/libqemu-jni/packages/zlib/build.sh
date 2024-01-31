PACKAGE_VERSION="1.3"
PACKAGE_SRCURL="https://www.zlib.net/zlib-$PACKAGE_VERSION.tar.xz"
PACKAGE_SHA256="8a9ba2898e1d0d774eca6ba5b4627a11e5588ba85c8851336eb38de4683050a7"

builder_step_configure() {
	CFLAGS+=" $CPPFLAGS -fPIC"
	LDFLAGS+=" -fPIC"
	"$PACKAGE_SRCDIR"/configure \
		--prefix="$PACKAGE_INSTALL_PREFIX" --static
}
