#!/usr/bin/env bash
set -euo pipefail

: "${JBOSS_HOME:=/opt/eap}"
: "${JBOSS_SERVER_LOG_DIR:=/var/local/jboss/eap/standalone/log}"
: "${JBOSS_APP_LOG_DIR:=/var/logs/applogs}"

mkdir -p "${JBOSS_SERVER_LOG_DIR}" "${JBOSS_APP_LOG_DIR}"

cli_file="$(mktemp)"
cleanup() {
  rm -f "${cli_file}"
}
trap cleanup EXIT

cat >"${cli_file}" <<EOF
embed-server --server-config=standalone.xml --std-out=echo
if (outcome == success) of /subsystem=naming/binding=java\:\/comp\/env\/server-log-root:read-resource
    /subsystem=naming/binding=java\:\/comp\/env\/server-log-root:write-attribute(name=value,value="${JBOSS_SERVER_LOG_DIR}")
else
    /subsystem=naming/binding=java\:\/comp\/env\/server-log-root:add(binding-type=simple,type=java.lang.String,value="${JBOSS_SERVER_LOG_DIR}")
end-if
if (outcome == success) of /subsystem=naming/binding=java\:\/comp\/env\/app-log-root:read-resource
    /subsystem=naming/binding=java\:\/comp\/env\/app-log-root:write-attribute(name=value,value="${JBOSS_APP_LOG_DIR}")
else
    /subsystem=naming/binding=java\:\/comp\/env\/app-log-root:add(binding-type=simple,type=java.lang.String,value="${JBOSS_APP_LOG_DIR}")
end-if
stop-embedded-server
EOF

"${JBOSS_HOME}/bin/jboss-cli.sh" --file="${cli_file}"

exec "${JBOSS_HOME}/bin/standalone.sh" "$@"
