PACKAGE_VERSION="24"
PACKAGE_SKIP_SRC_EXTRACT="true"

builder_step_make_install() {
	local sources="$PACKAGE_BUILDER_DIR/sources/src/musl-*/*.c"

	"$CC" $CFLAGS -std=c99 -DNULL=0 $CPPFLAGS \
		-I"$PACKAGE_BUILDER_DIR/sources/src/include" \
		-c $sources
	"$AR" rcs libandroid-support.a *.o

	install -Dm600 libandroid-support.a "$PACKAGE_INSTALL_PREFIX/lib/libandroid-support.a"
}
