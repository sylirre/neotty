FROM alpine:edge

ARG USER_ID 1000
ARG RSA_KEY_PASSPHRASE pass

COPY /abuild-keys /abuild-keys
COPY /extras /custom-packages/extras

RUN apk upgrade && \
    apk add alpine-sdk build-base apk-tools alpine-conf openssl \
        busybox fakeroot syslinux xorriso squashfs-tools sudo && \
    adduser -s /bin/ash -G abuild -D -h /home/build -u ${USER_ID} build && \
    mkdir -p /home/build/.abuild && chown build /home/build/.abuild && \
    openssl aes-256-cbc -d -in /abuild-keys/neotty.rsa.enc \
        -out /home/build/.abuild/neotty.rsa \
        -k "$RSA_KEY_PASSPHRASE" -pbkdf2 && \
    chown build /home/build/.abuild/neotty.rsa && \
    chmod 600 /home/build/.abuild/neotty.rsa && \
    cp /abuild-keys/neotty.rsa.pub /home/build/.abuild/neotty.rsa.pub && \
    chown build /home/build/.abuild/neotty.rsa.pub && \
    cp /abuild-keys/neotty.rsa.pub /etc/apk/keys/neotty.rsa.pub && \
    chown root:root /etc/apk/keys/neotty.rsa.pub && \
    chmod 644 /etc/apk/keys/neotty.rsa.pub && \
    echo "PACKAGER_PRIVKEY=/home/build/.abuild/neotty.rsa" > \
        /home/build/.abuild/abuild.conf && \
    chown build /home/build/.abuild/abuild.conf && \
    chmod 644 /home/build/.abuild/abuild.conf && \
    chown -Rh build:abuild /custom-packages && \
    echo "build ALL=(ALL:ALL) NOPASSWD: ALL" > /etc/sudoers.d/build

USER build

CMD ["/bin/ash"]
