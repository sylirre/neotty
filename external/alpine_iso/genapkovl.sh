#!/bin/sh -e

umask 0022

cleanup() {
	rm -rf "$tmp"
}

makefile() {
	OWNER="$1"
	PERMS="$2"
	FILENAME="$3"
	cat > "$FILENAME"
	chown "$OWNER" "$FILENAME"
	chmod "$PERMS" "$FILENAME"
}

rc_add() {
	mkdir -p "$tmp"/etc/runlevels/"$2"
	ln -sf /etc/init.d/"$1" "$tmp"/etc/runlevels/"$2"/"$1"
}

tmp="$(mktemp -d)"
trap cleanup EXIT

mkdir -p "$tmp"/etc

##############################################################################
##
## File system mounts (fstab)
##
##############################################################################

makefile root:root 0644 "$tmp"/etc/fstab <<EOF
/dev/sr0        /media/sr0      iso9660  ro                         0 0
shared_storage  /media/host     9p       trans=virtio,msize=262144  0 0
EOF

##############################################################################
##
## Login banner (base64 encoded)
##
##############################################################################

cat "${MKIMAGE_SCRIPT_DIR}"/files/issue | makefile root:root 0644 "$tmp"/etc/issue

##############################################################################
##
## MOTD (base64 encoded)
##
##############################################################################

cat "${MKIMAGE_SCRIPT_DIR}"/files/motd | makefile root:root 0644 "$tmp"/etc/motd

##############################################################################
##
## Package manager configuration
##
##############################################################################

## Repository list.
## Enable only CD-ROM by default.
mkdir -p "$tmp"/etc/apk
makefile root:root 0644 "$tmp"/etc/apk/repositories <<EOF
/media/sr0/apks
http://dl-cdn.alpinelinux.org/alpine/edge/main
http://dl-cdn.alpinelinux.org/alpine/edge/community
#http://dl-cdn.alpinelinux.org/alpine/edge/testing
EOF

## World file (defines packages installed on ISO boot).
makefile root:root 0644 "$tmp"/etc/apk/world <<EOF
alpine-base
bash
curl
e2fsprogs
e2fsprogs-extra
libc6-compat
lynx
nano
nano-syntax
openssh-client
openssl
syslinux
tmux
vim
EOF

##############################################################################
##
## Bash configuration
##
##############################################################################

mkdir -p "$tmp"/etc/skel

## Bashrc
makefile root:root 0600 "$tmp"/etc/skel/.bashrc <<'EOF'
# ~/.bashrc: executed by bash(1) for non-login shells.

# If not running interactively, don't do anything.
[[ $- != *i* ]] && return

# On serial console we need to be sure whether console size is matching
# the size of a terminal screen.
if [[ "$(tty)" == /dev/ttyS* ]]; then
	PS0='$(eval "$(resize)")'

	if [ "$SHLVL" = "1" ]; then
		eval "$(resize)"
		printf '\033[!p'
	fi
fi

# Notify user when rootfs is placed on tmpfs which is default configuration.
if [[ $(grep "[[:space:]]/[[:space:]]" /proc/mounts | cut -d' ' -f3) == "tmpfs" ]]; then
	if [ "$SHLVL" = "1" ]; then
		printf '\n\e[1;93mNOTE: \e[0;33mrunning live system, all changes will be discarded on reboot.\e[0m\n\n'
	fi
fi

# Dynamically updated shell prompt.
_update_bash_prompt() {
	# Add user@host prefix when connected over SSH.
	local user_host
	if [ -n "$SSH_CLIENT" ] && [ -z "$PROMPT_DISABLE_USERHOST" ]; then
		user_host="\\[\\e[1;35m\\]\\u\\[\\e[1;34m\\]@\\[\\e[1;35m\\]\\h\\[\\e[0m\\] "
	else
		user_host=""
	fi

	# Add Git branch information.
	local git_branch=""
	if [ -n "$(command -v git)" ]; then
		git_branch=$(git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \(.*\)/\\[\\e[0;33m\\](\1)\\[\\e[0m\\] /')
	fi

	local py_virtualenv=""
	if [ -n "$VIRTUAL_ENV" ]; then
		py_virtualenv="\\[\\e[0;90m\\]($(basename "$VIRTUAL_ENV"))\\[\\e[0m\\] "
	fi

	# Finally build the PS1 prompt.
	PS1="${py_virtualenv}${user_host}\\[\\e[0;32m\\]\\w\\[\\e[0m\\] ${git_branch}\\[\\e[0;97m\\]\\$\\[\\e[0m\\] "

	# Set title for Xterm-compatible terminals.
	if [[ "$TERM" =~ ^(xterm|rxvt) ]]; then
		PS1="\\[\\e]0;\\u@\\h: \\w\\a\\]${PS1}"
	fi
}
PROMPT_COMMAND="_update_bash_prompt;"

# Show 2 directories of CWD context.
PROMPT_DIRTRIM=2

# Secondary prompt configuration.
PS2='> '
PS3='> '
PS4='+ '

# Shell options.
shopt -s checkwinsize
shopt -s cmdhist
shopt -s globstar
shopt -s histappend
shopt -s histverify

# Configure bash history.
HISTSIZE=5000
HISTFILESIZE=1000000
HISTCONTROL="ignoreboth"
HISTTIMEFORMAT="%Y/%m/%d %T : "

# Specify default text editor.
export EDITOR=vim

# Colorful output of GCC.
export GCC_COLORS='error=01;31:warning=01;35:note=01;36:caret=01;32:locus=01:quote=01'

# Colorful output of ls.
export LS_COLORS='rs=0:di=01;34:ln=01;36:mh=00:pi=40;33:so=01;35:do=01;35:bd=40;33;01:cd=40;33;01:or=01;05;37;41:mi=01;05;37;41:su=37;41:sg=30;43:ca=30;41:tw=30;42:ow=34;42:st=37;44:ex=01;32:*.tar=01;31:*.tgz=01;31:*.arc=01;31:*.arj=01;31:*.taz=01;31:*.lha=01;31:*.lz4=01;31:*.lzh=01;31:*.lzma=01;31:*.tlz=01;31:*.txz=01;31:*.tzo=01;31:*.t7z=01;31:*.zip=01;31:*.z=01;31:*.Z=01;31:*.dz=01;31:*.gz=01;31:*.lrz=01;31:*.lz=01;31:*.lzo=01;31:*.xz=01;31:*.bz2=01;31:*.bz=01;31:*.tbz=01;31:*.tbz2=01;31:*.tz=01;31:*.deb=01;31:*.rpm=01;31:*.jar=01;31:*.war=01;31:*.ear=01;31:*.sar=01;31:*.rar=01;31:*.alz=01;31:*.ace=01;31:*.zoo=01;31:*.cpio=01;31:*.7z=01;31:*.rz=01;31:*.cab=01;31:*.jpg=01;35:*.jpeg=01;35:*.gif=01;35:*.bmp=01;35:*.pbm=01;35:*.pgm=01;35:*.ppm=01;35:*.tga=01;35:*.xbm=01;35:*.xpm=01;35:*.tif=01;35:*.tiff=01;35:*.png=01;35:*.svg=01;35:*.svgz=01;35:*.mng=01;35:*.pcx=01;35:*.mov=01;35:*.mpg=01;35:*.mpeg=01;35:*.m2v=01;35:*.mkv=01;35:*.webm=01;35:*.ogm=01;35:*.mp4=01;35:*.m4v=01;35:*.mp4v=01;35:*.vob=01;35:*.qt=01;35:*.nuv=01;35:*.wmv=01;35:*.asf=01;35:*.rm=01;35:*.rmvb=01;35:*.flc=01;35:*.avi=01;35:*.fli=01;35:*.flv=01;35:*.gl=01;35:*.dl=01;35:*.xcf=01;35:*.xwd=01;35:*.yuv=01;35:*.cgm=01;35:*.emf=01;35:*.ogv=01;35:*.ogx=01;35:*.cfg=00;32:*.conf=00;32:*.diff=00;32:*.doc=00;32:*.ini=00;32:*.log=00;32:*.patch=00;32:*.pdf=00;32:*.ps=00;32:*.tex=00;32:*.txt=00;32:*.aac=00;36:*.au=00;36:*.flac=00;36:*.m4a=00;36:*.mid=00;36:*.midi=00;36:*.mka=00;36:*.mp3=00;36:*.mpc=00;36:*.ogg=00;36:*.ra=00;36:*.wav=00;36:*.oga=00;36:*.opus=00;36:*.spx=00;36:*.xspf=00;36:*.desktop=01;35:'

# Specify default pager.
export PAGER="less"

# Enable colored output.
#alias grep='grep --color=auto'
#alias dir='dir --color=auto'
#alias egrep='egrep --color=auto'
#alias fgrep='fgrep --color=auto'
#alias l='ls --color=auto --group-directories-first'
#alias ls='ls --color=auto --group-directories-first'
#alias l.='ls --color=auto -d --group-directories-first .*'
#alias la='ls --color=auto -a --group-directories-first'
#alias ll='ls --color=auto -Fhl --group-directories-first'
#alias ll.='ls --color=auto -Fhl -d --group-directories-first .*'
#alias vdir='vdir --color=auto -h'

# Safety.
#alias cp='cp -i'
#alias ln='ln -i'
#alias mv='mv -i'
#alias rm='rm -i --preserve-root'
EOF

## Bash profile
makefile root:root 0600 "$tmp"/etc/skel/.bash_profile <<'EOF'
# ~/.bash_profile: executed by the command interpreter for login shells.
[ -f "${HOME}/.bashrc" ] && . "${HOME}/.bashrc"
EOF

## Copy skel to /root.
mkdir -p "$tmp"/root
chmod 700 "$tmp"/root
cp -R "$tmp"/etc/skel/. "$tmp"/root/

# Add skel files to lbu protected paths.
mkdir -p "$tmp"/etc/apk/protected_paths.d
makefile root:root 0644 "$tmp"/etc/apk/protected_paths.d/lbu.list <<'EOF'
+root/.bash_profile
+root/.bashrc
+root/README.txt
+usr/local
EOF

##############################################################################
##
## Sysctl
##
##############################################################################

## Disable ASLR.
mkdir -p "$tmp"/etc/sysctl.d
makefile root:root 0644 "$tmp"/etc/sysctl.d/no-aslr.conf <<EOF
# ASLR degrades performance when system is running under QEMU.
kernel.randomize_va_space=0
EOF

##############################################################################
##
## Network
##
##############################################################################

## Host name.
makefile root:root 0644 "$tmp"/etc/hostname <<EOF
neotty
EOF

## IP/hostname mappings.
makefile root:root 0644 "$tmp"/etc/hosts <<EOF
# IPv4.
127.0.0.1   localhost.localdomain localhost
127.0.1.1   neotty qemu-vm
10.0.2.2    qemu-host
10.0.2.3    qemu-dns

# IPv6.
::1         ip6-localhost ip6-loopback
fe00::0     ip6-localnet
ff00::0     ip6-mcastprefix
ff02::1     ip6-allnodes
ff02::2     ip6-allrouters
ff02::3     ip6-allhosts
EOF

## Network interface configuration
mkdir -p "$tmp"/etc/network
makefile root:root 0644 "$tmp"/etc/network/interfaces <<EOF
auto lo
iface lo inet loopback

auto eth0
iface eth0 inet static
    address 10.0.2.15
    netmask 255.255.255.0
    gateway 10.0.2.2
EOF
makefile root:root 0644 "$tmp"/etc/resolv.conf <<EOF
nameserver 8.8.8.8
nameserver 10.0.2.3
EOF

##############################################################################
##
## Services
##
##############################################################################

## Configuration.
mkdir -p "$tmp"/etc/conf.d
makefile root:root 0644 "$tmp"/etc/conf.d/bootmisc <<EOF
# List of /tmp directories we should clean up
clean_tmp_dirs="/tmp"

# Should we wipe the tmp paths completely or just selectively remove known
# locks / files / etc... ?
wipe_tmp="NO"

# Write the initial dmesg log into /var/log/dmesg after boot
# This may be useful if you need the kernel boot log afterwards
log_dmesg="NO"

# Save the previous dmesg log to dmesg.old
# This may be useful if you need to compare the current boot to the
# previous one.
#previous_dmesg=no
EOF

## Inittab
makefile root:root 0644 "$tmp"/etc/inittab <<EOF
# /etc/inittab

::sysinit:/sbin/openrc sysinit
::sysinit:/sbin/openrc boot
::wait:/sbin/openrc default

# Stuff to do for the 3-finger salute
::ctrlaltdel:/sbin/reboot

# Stuff to do before rebooting
::shutdown:/sbin/openrc shutdown

# Login via serial line
ttyS0::respawn:/sbin/getty -L ttyS0 115200 xterm-256color

# Enable login on alternative console
tty1::respawn:/sbin/getty -L 0 tty1 linux
EOF

## Runlevels
rc_add devfs sysinit
rc_add dmesg sysinit
rc_add mdev sysinit
rc_add hwdrivers sysinit
rc_add modloop sysinit
rc_add hwclock boot
rc_add modules boot
rc_add sysctl boot
rc_add hostname boot
rc_add bootmisc boot
rc_add syslog boot
rc_add networking boot
rc_add ntpd default
rc_add mount-ro shutdown
rc_add killprocs shutdown
rc_add savecache shutdown

##############################################################################
##
## Users and groups
##
##############################################################################
# Files passwd, group & shadow must be kept in sync!

## Users
makefile root:root 0644 "$tmp"/etc/passwd <<EOF
root:x:0:0:root:/root:/bin/bash
bin:x:1:1:bin:/bin:/sbin/nologin
daemon:x:2:2:daemon:/sbin:/sbin/nologin
adm:x:3:4:adm:/var/adm:/sbin/nologin
lp:x:4:7:lp:/var/spool/lpd:/sbin/nologin
sync:x:5:0:sync:/sbin:/bin/sync
shutdown:x:6:0:shutdown:/sbin:/sbin/shutdown
halt:x:7:0:halt:/sbin:/sbin/halt
mail:x:8:12:mail:/var/mail:/sbin/nologin
news:x:9:13:news:/usr/lib/news:/sbin/nologin
uucp:x:10:14:uucp:/var/spool/uucppublic:/sbin/nologin
operator:x:11:0:operator:/root:/sbin/nologin
man:x:13:15:man:/usr/man:/sbin/nologin
postmaster:x:14:12:postmaster:/var/mail:/sbin/nologin
cron:x:16:16:cron:/var/spool/cron:/sbin/nologin
ftp:x:21:21::/var/lib/ftp:/sbin/nologin
sshd:x:22:22:sshd:/dev/null:/sbin/nologin
at:x:25:25:at:/var/spool/cron/atjobs:/sbin/nologin
squid:x:31:31:Squid:/var/cache/squid:/sbin/nologin
xfs:x:33:33:X Font Server:/etc/X11/fs:/sbin/nologin
games:x:35:35:games:/usr/games:/sbin/nologin
cyrus:x:85:12::/usr/cyrus:/sbin/nologin
vpopmail:x:89:89::/var/vpopmail:/sbin/nologin
ntp:x:123:123:NTP:/var/empty:/sbin/nologin
smmsp:x:209:209:smmsp:/var/spool/mqueue:/sbin/nologin
guest:x:405:100:guest:/dev/null:/sbin/nologin
nobody:x:65534:65534:nobody:/:/sbin/nologin
EOF

## Groups
makefile root:root 0644 "$tmp"/etc/group <<EOF
root:x:0:root
bin:x:1:root,bin,daemon
daemon:x:2:root,bin,daemon
sys:x:3:root,bin,adm
adm:x:4:root,adm,daemon
tty:x:5:
disk:x:6:root,adm
lp:x:7:lp
mem:x:8:
kmem:x:9:
wheel:x:10:root
floppy:x:11:root
mail:x:12:mail
news:x:13:news
uucp:x:14:uucp
man:x:15:man
cron:x:16:cron
console:x:17:
audio:x:18:
cdrom:x:19:
dialout:x:20:root
ftp:x:21:
sshd:x:22:
input:x:23:
at:x:25:at
tape:x:26:root
video:x:27:root
netdev:x:28:
readproc:x:30:
squid:x:31:squid
xfs:x:33:xfs
kvm:x:34:
games:x:35:
shadow:x:42:
cdrw:x:80:
www-data:x:82:
usb:x:85:
vpopmail:x:89:
users:x:100:games
ntp:x:123:
nofiles:x:200:
smmsp:x:209:smmsp
locate:x:245:
abuild:x:300:
utmp:x:406:
ping:x:999:
nogroup:x:65533:
nobody:x:65534:
EOF

## Shadow
makefile root:shadow 0640 "$tmp"/etc/shadow <<EOF
root:::0:::::
bin:!::0:::::
daemon:!::0:::::
adm:!::0:::::
lp:!::0:::::
sync:!::0:::::
shutdown:!::0:::::
halt:!::0:::::
mail:!::0:::::
news:!::0:::::
uucp:!::0:::::
operator:!::0:::::
man:!::0:::::
postmaster:!::0:::::
cron:!::0:::::
ftp:!::0:::::
sshd:!::0:::::
at:!::0:::::
squid:!::0:::::
xfs:!::0:::::
games:!::0:::::
cyrus:!::0:::::
vpopmail:!::0:::::
ntp:!::0:::::
smmsp:!::0:::::
guest:!::0:::::
nobody:!::0:::::
EOF

##############################################################################
##
## Documentation
##
##############################################################################

# Application guide.
cat "${MKIMAGE_SCRIPT_DIR}"/files/motd | sed -e $'s/\x1b\[[0-9;]*m//g' \
	> "$tmp"/etc/skel/README.txt
cp "$tmp"/etc/skel/README.txt "$tmp"/root/README.txt
chmod 600 "$tmp"/etc/skel/README.txt "$tmp"/root/README.txt

##############################################################################
##
## /usr/local
##
##############################################################################

mkdir -p "$tmp"/usr/local/bin "$tmp"/usr/local/share
cp "${MKIMAGE_SCRIPT_DIR}"/files/local-bin/* "$tmp"/usr/local/bin/
cp -r "${MKIMAGE_SCRIPT_DIR}"/files/local-share/* "$tmp"/usr/local/share/
chown -R root:root "$tmp"/usr/local
find "$tmp"/usr/local -type d -exec chmod 755 "{}" \;
find "$tmp"/usr/local/bin -type f -exec chmod 755 "{}" \;
find "$tmp"/usr/local/share -type f -exec chmod 644 "{}" \;

##############################################################################
##
## END
##
##############################################################################

tar -c -C "$tmp" etc root usr | gzip -9n > defaults.apkovl.tar.gz
