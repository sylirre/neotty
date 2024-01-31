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
donation using Litecoin, Ethereum or Tron cryptocurrency:**

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
- Build own programs
- Host a local web application
- Access remote server using SSH
- Do hacking of any kind (except where hardware access required)
- Hide content on LUKS encrypted volume

Many common packages are bundled within intergated [Alpine Linux] ISO file to
make the NeoTTY suitable for offline use in air gapped conditions.

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
