PACKAGE_VERSION="3.3"
PACKAGE_SRCURL="ftp://sourceware.org/pub/libffi/libffi-${PACKAGE_VERSION}.tar.gz"
PACKAGE_SHA256="72fba7922703ddfa7a028d513ac15a85c8d54c8d67f55fa5a4802885dc652056"
PACKAGE_EXTRA_CONFIGURE_ARGS="--disable-multi-os-directory"

builder_step_pre_configure() {
	if [ "$PACKAGE_TARGET_ARCH" = "arm" ]; then
		CFLAGS+=" -fno-integrated-as"
	fi
	autoconf
}

builder_step_post_configure() {
	# Work around since mmap can't be written and marked
	# executable in android anymore from userspace.
	echo "#define FFI_MMAP_EXEC_WRIT 1" >> fficonfig.h
}
