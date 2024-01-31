PACKAGE_VERSION="2.58.3"
PACKAGE_SRCURL="https://ftp.gnome.org/pub/gnome/sources/glib/${PACKAGE_VERSION:0:4}/glib-${PACKAGE_VERSION}.tar.xz"
PACKAGE_SHA256="8f43c31767e88a25da72b52a40f3301fefc49a665b56dc10ee7cc9565cbe7481"
PACKAGE_DEPENDS="libandroid-support, libffi, libiconv, zlib"

# --enable-compile-warnings=no to get rid of format strings causing errors.
PACKAGE_EXTRA_CONFIGURE_ARGS="
--cache-file=custom_configure.cache
--disable-compile-warnings
--disable-gtk-doc
--disable-gtk-doc-html
--disable-libelf
--disable-libmount
--with-pcre=internal
--with-iconv=external
"

builder_step_pre_configure() {
	# Use autotools.
	rm -f meson.build
	NOCONFIGURE=1 ./autogen.sh

	# glib checks for __BIONIC__ instead of __ANDROID__:
	CFLAGS="$CFLAGS -D__BIONIC__=1 -fPIC"
	LDFLAGS="${LDFLAGS/-static/} -fPIC"

	cd "${PACKAGE_BUILDDIR}"

	# https://developer.gnome.org/glib/stable/glib-cross-compiling.html
	echo "glib_cv_long_long_format=ll" >> custom_configure.cache
	echo "glib_cv_stack_grows=no" >> custom_configure.cache
	echo "glib_cv_uscore=no" >> custom_configure.cache
	chmod a-w custom_configure.cache
}
