libandroid-support
------------------
A copy of libandroid-support as built from NDK r14.

The purpose of `libandroid-support` is to add functionality missing
the system libc to ease building packages.

What is still necessary?
------------------------
Some functionality can probably be removed in favour of functionality in bionic:

- [bionic in Android 5.0](https://android.googlesource.com/platform/bionic.git/+/lollipop-release/libc/bionic/)
- [bionic in Android 6.0](https://android.googlesource.com/platform/bionic.git/+/marshmallow-release/libc/bionic/)
- [bionic in Android 7.0](https://android.googlesource.com/platform/bionic.git/+/nougat-release/libc/bionic/)

Test cases
----------
- Findutils: `touch åäö && find . -name åäö`.
