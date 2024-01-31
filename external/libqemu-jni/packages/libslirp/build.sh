PACKAGE_VERSION="4.7.0"
PACKAGE_SRCURL="https://gitlab.freedesktop.org/slirp/libslirp/-/archive/v${PACKAGE_VERSION}/libslirp-v${PACKAGE_VERSION}.tar.gz"
PACKAGE_SHA256="9398f0ec5a581d4e1cd6856b88ae83927e458d643788c3391a39e61b75db3d3b"
PACKAGE_DEPENDS="libiconv"

builder_step_post_make_install() {
	rm -f "$PACKAGE_INSTALL_PREFIX"/lib/libslirp.so
}
