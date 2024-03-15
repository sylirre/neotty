# NeoTTY

*A new terminal app for Android devices...*

NeoTTY was started as an experimental fork of [Termux] terminal emulator
project and is focused on providing full-fledged Linux system usage
experience. This app runs a headless [x86_64 emulated](https://qemu.org)
machine with [Alpine Linux] operating system. User device is not required
to be rooted or have any other kind of special configuration.

Best experience is achievable on top-spec devices with Snapdragon 8 Gen 2
(newer) and 50+ GB of free disk space. Please consider that NeoTTY typically
will run in parallel with other apps and you will want enough resources for
everything.

This app is not a subject for [Android OS] compatibility issues and
restrictions for [binary execution] and [child processes].

> *Disclaimer: this project initially was made for personal use. Assuming it
could be useful for someone else, I decided to share it free of charge.
As Android development is not my occupation, bug reports and feature requests
will not be processed.*
> 
> *Repository normally will be kept in archived state with updates published
once in a long while.*

**If you find NeoTTY useful, please consider supporting developer by making a
donation using cryptocurrency:**

* Bitcoin: `bc1qp4paa22t8l2apwfuujp6des6e90p7lm8zmhd7s`
* Litecoin: `ltc1q2yne7e2p5ypf2ky0j3tg3vd6yktd5u57rmlly9`
* Ethereum: `0x76c7f1FC3C7396396fBD7e8cBDc2fc741FFa4aFa`
* Tron: `TUP941DmHfrBNxvbcYkThx9hHrskU7FyTa`

## How can you use this app

NeoTTY similar to personal computer device, but without display. You are
given a virtual serial console terminal, cdrom with [Alpine Linux], two empty
hard disks and network connection. As soon as your tasks do not require
graphics, high performance or real hardware access, you are free in possible
actions.

Few ideas to get started:

- Explore the world of Linux and Open Source Software =)

  NeoTTY provides you a playground with a full-fledged Linux installation.
  If broke something, just reset the app and begin with fresh environment.
  Your changes won't affect host device.

- Build own programs with ASM, C, C++, Go, Java, Node.js, Python, Rust, etc

  Program with ASM, C, C++, Go, Java, Node.js, Python, Rust or any other
  language you want. The most common build tools are available in Alpine
  Linux repositories. If not, you can try compiling them from source.

- Host a dockerized web application

  Experiments confirmed that one can host a complex web application inside
  QEMU on mobile device with enough performance for 1 - 3 users.

  Although with something like NextCloud performance lags are expected.

- Access remote servers

  Easily SSH into remote server or mount cloud storage as local directory.
  All necessary software is available.

- Do hacking of any kind (except where hardware access required)

  Pentesting, reverse engineering... you know

  However access to real hardware is disabled by design and will never be
  implemented. Don't try to use NeoTTY to root your Android device or
  access USB dongles. This isn't going to work.

- Hide content on LUKS encrypted volume

  Although there is no 100% proof, the NeoTTY app should be fully resistant to
  possible user data proactive scanning that can be initiated by Google
  or manufacturer if they would be obligated by ~~piece of paper~~ law.

  Beware of forensics guys. They are triggered by presence of encryption
  rather than by actual content. Keep your device away from third party
  persons (applicable even if you are not user of NeoTTY :)).

Many common packages are bundled within intergated [Alpine Linux] ISO file to
make the NeoTTY suitable for offline use and make it self-sufficient in SHTF
conditions. This includes basic system utilities; software build tools such
as compilers, interpreters and development libraries; text editors; archivers
and compressors; encryption utilities; backup utilities; media file format
utilities. APK file size expected to be unusually big but I tend to keep it
around 2GB.

![screenshot](/images/neotty_preview.png)

The instructions on how to use the application are shown by `/etc/motd` after
login. If you need details on how to work with [Alpine Linux], please visit
its [Wiki](https://wiki.alpinelinux.org/wiki/Main_Page).

Noting that NeoTTY is for people who have at least basic experience of
installing and working with Linux systems. If you are not familiar with this,
have troubles with shell scripting or just not comfortable with text-only
interfaces, then NeoTTY will not provide you a great experience.

[Termux]: https://termux.dev
[Alpine Linux]: https://alpinelinux.org
[Android OS]: https://www.android.com
[binary execution]: https://github.com/termux/termux-app/issues/2155
[child processes]: https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md#phantom-processes
