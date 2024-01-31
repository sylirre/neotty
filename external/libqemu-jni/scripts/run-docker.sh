#!/bin/sh
set -e -u

REPOROOT=$(dirname "$(readlink -f "${0}")")/../

BUILDENV_USER="builder"
BUILDENV_HOME="/home/builder"
: "${BUILDENV_IMAGE_NAME:="neotty-buildenv"}"
: "${BUILDENV_CONTAINER_NAME:="neotty-buildenv"}"
: "${BUILDENV_RUN_WITH_SUDO:=false}"

if [ "${BUILDENV_RUN_WITH_SUDO}" = "true" ]; then
	SUDO="sudo"
else
	SUDO=
fi

if [ "${GITHUB_EVENT_PATH-x}" != "x" ]; then
	# On CI/CD tty may not be available.
	DOCKER_TTY=""
else
	DOCKER_TTY="--tty"
fi

echo "Running container '${BUILDENV_CONTAINER_NAME}' from image '${BUILDENV_IMAGE_NAME}'..."

$SUDO docker start "${BUILDENV_CONTAINER_NAME}" > /dev/null 2> /dev/null || {
	echo "Creating new container..."

	$SUDO docker run --detach --tty \
		--name "${BUILDENV_CONTAINER_NAME}" \
		--volume "${REPOROOT}:${BUILDENV_HOME}/libqemu-jni" \
		"${BUILDENV_IMAGE_NAME}"

	if [ "$(id -u)" -ne 1000 ] && [ "$(id -u)" -ne 0 ]; then
		echo "Changed builder uid/gid... (this may take a while)"
		$SUDO docker exec ${DOCKER_TTY} "${BUILDENV_CONTAINER_NAME}" \
			sudo chown -Rh "$(id -u):$(id -g)" /data "${BUILDENV_HOME}"
		$SUDO docker exec ${DOCKER_TTY} "${BUILDENV_CONTAINER_NAME}" \
			sudo usermod -u "$(id -u)" "${BUILDENV_USER}"
		$SUDO docker exec ${DOCKER_TTY} "${BUILDENV_CONTAINER_NAME}" \
			sudo groupmod -g "$(id -g)" "${BUILDENV_USER}"
	fi
}

if [ "$#" -eq  "0" ]; then
	$SUDO docker exec --interactive ${DOCKER_TTY} \
		--user "${BUILDENV_USER}" \
		"${BUILDENV_CONTAINER_NAME}" /bin/bash
else
	$SUDO docker exec --interactive ${DOCKER_TTY} \
		--user "${BUILDENV_USER}" \
		"${BUILDENV_CONTAINER_NAME}" "${@}"
fi
