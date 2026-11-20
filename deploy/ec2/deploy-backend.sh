#!/usr/bin/env bash
set -euo pipefail

release=${1:?missing commit SHA}
checksum=${2:?missing JAR checksum}
[[ $release =~ ^[0-9a-f]{40}$ && $checksum =~ ^[0-9a-f]{64}$ ]]
source /etc/maeum/deploy.conf

releases=/opt/maeum/releases
current=/opt/maeum/current.jar
service=maeum-backend.service
install -d -m 0755 "$releases"
exec 9>/run/lock/maeum-deploy.lock
flock -x 9

release_jar="$releases/$release.jar"
download=$(mktemp "$releases/.download.XXXXXX")
trap 'rm -f -- "$download" "${current}.next"' EXIT
aws s3 cp "s3://${DEPLOY_BUCKET:?unset}/backend/$release.jar" "$download" --only-show-errors
printf '%s  %s\n' "$checksum" "$download" | sha256sum --check --status
chmod 0644 "$download"
mv -fT "$download" "$release_jar"

previous=$(readlink "$current" || true)
point_current() {
    ln -sfn "$1" "${current}.next"
    mv -Tf "${current}.next" "$current"
}

wait_for_health() {
    for ((attempt = 0; attempt < 45; attempt++)); do
        if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health 2>/dev/null |
            grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
            return 0
        fi
        sleep 2
    done
    return 1
}

point_current "$release_jar"
if systemctl restart "$service" && wait_for_health; then
    printf '%s\n' "Deployed $release"
    exit 0
fi

printf '%s\n' "Deployment failed for $release" >&2
if [[ -n $previous && $previous != "$release_jar" ]]; then
    point_current "$previous"
    if systemctl restart "$service" && wait_for_health; then
        printf '%s\n' "Restored previous JAR"
    else
        printf '%s\n' "Previous JAR did not pass the health check" >&2
    fi
else
    systemctl stop "$service"
    if [[ -z $previous ]]; then
        rm -f -- "$current"
    fi
fi
exit 1
