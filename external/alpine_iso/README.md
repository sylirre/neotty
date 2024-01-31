# Alpine Linux ISO build scripts

Everything that is required to build a custom Alpine Linux ISO image. For now
the image is based on Edge & Testing branches which allow to use the latest
available package versions. Even though it could be a bit unstable and have
certain packaging issues, they should not be a problem in most cases.

The created ISO image is intended to be used for running a live system without
the need of hard drive. Root file system is placed on RAM (tmpfs). In order
to install system on disk, user must run the standard steps of installation
shown on [Wiki](https://wiki.alpinelinux.org/wiki/Installation).

**Alpine Linux is developed by third parties and is not affiliated with the
NeoTTY application in any way.**

# How to build ISO image

Requires `docker` to be installed and running.

Once you've done with `docker` configuration, do:

 make build-docker

The make target 'build-docker' will perform ISO building in Docker container
as scripts require operating system to be Alpine Linux and would not work on
others. The generated files are placed into '$(pwd)/iso' by default.

To specify custom output directory, pass argument 'OUTPUT_DIR=/path' to the
'make' command. The specified path must be absolute.
