PACKAGE_VERSION="ndk-r20"
PACKAGE_SKIP_SRC_EXTRACT="true"

builder_step_make_install() {
	local sources="$PACKAGE_BUILDER_DIR/sources/cpu-features.c"

	"$CC" $CFLAGS $CPPFLAGS \
		-I"$PACKAGE_BUILDER_DIR/sources" \
		-c $sources
	"$AR" rcs libandroid-cpufeatures.a *.o

	install -Dm600 "$PACKAGE_BUILDER_DIR/sources/cpu-features.h" \
		"$PACKAGE_INSTALL_PREFIX/include/cpu-features.h"
	install -Dm600 libandroid-cpufeatures.a \
		"$PACKAGE_INSTALL_PREFIX/lib/libandroid-cpufeatures.a"
}
