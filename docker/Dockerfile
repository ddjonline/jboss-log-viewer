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

# JBOSS_HOME is provided by the base image (/opt/eap). Define the source-of-truth
# environment values that the startup script writes into standalone.xml as JNDI
# simple bindings before EAP starts.
ENV JBOSS_SERVER_LOG_DIR=/var/local/jboss/eap/standalone/log \
    JBOSS_APP_LOG_DIR=/var/logs/applogs

# Create the application log directory with group-writable perms (the EAP images
# run as an arbitrary UID in group root on OpenShift; jboss:root keeps it valid
# locally too).
USER root
COPY docker/configure-jndi-and-start.sh /opt/eap/bin/configure-jndi-and-start.sh
RUN mkdir -p /var/local/jboss/eap/standalone/log /var/logs/applogs \
    && chown -R jboss:root /var/local/jboss /var/logs/applogs /opt/eap/bin/configure-jndi-and-start.sh \
    && chmod -R g+rwX /var/local/jboss /var/logs/applogs \
    && chmod 0755 /opt/eap/bin/configure-jndi-and-start.sh
USER jboss

ENTRYPOINT ["/opt/eap/bin/configure-jndi-and-start.sh"]

# The custom entrypoint configures JNDI and then starts the server. Expose the
# HTTP and management ports.
EXPOSE 8080 9990
