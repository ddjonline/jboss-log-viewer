# JBoss EAP 8.1 (OpenJDK 21) image for the JBoss Log Viewer.
#
# Uses the Java 21 runtime image to match the application's Java 21 target.
#
# The official Red Hat EAP 8.1 images live behind the authenticated Red Hat
# registry (registry.redhat.io) and require a Red Hat subscription. Log in
# before building:
#
#     podman login registry.redhat.io      # or: docker login registry.redhat.io
#
# The EAR is NOT baked into this image — it is mounted at run time into the
# server's deployments directory (see docker-compose.yml / the docker run
# command in the README), so you can rebuild the app without rebuilding the
# image.
#
# If you do not have a Red Hat subscription, see the WildFly alternative in the
# README, which runs the identical EAR from a freely pullable image.

ARG EAP_IMAGE=registry.redhat.io/jboss-eap-8/eap81-openjdk21-runtime-openshift-rhel9:latest
FROM ${EAP_IMAGE}

# JBOSS_HOME is provided by the base image (/opt/eap). Define the two log roots
# the application reads. The server log dir is EAP's own standalone/log; the
# application log dir defaults to a separate, mountable location.
ENV JBOSS_SERVER_LOG_DIR=${JBOSS_HOME}/standalone/log \
    JBOSS_APP_LOG_DIR=/var/log/app

# Create the application log directory with group-writable perms (the EAP images
# run as an arbitrary UID in group root on OpenShift; jboss:root keeps it valid
# locally too).
USER root
RUN mkdir -p /var/log/app && chown -R jboss:root /var/log/app && chmod -R g+rwX /var/log/app
USER jboss

# The runtime image already starts the server bound to all interfaces via its
# default entrypoint. Expose the HTTP and management ports.
EXPOSE 8080 9990
