{{/*
Common helpers for notebook-platform.
*/}}
{{- define "notebook-platform.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "notebook-platform.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.namespace" -}}
{{- default .Release.Namespace .Values.namespace.name -}}
{{- end -}}

{{- define "notebook-platform.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "notebook-platform.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.secretName" -}}
{{- if .Values.secrets.existingSecret -}}
{{- .Values.secrets.existingSecret -}}
{{- else if and (eq .Values.secrets.mode "external-secrets") .Values.secrets.externalSecrets.targetSecretName -}}
{{- .Values.secrets.externalSecrets.targetSecretName -}}
{{- else -}}
{{- default (printf "%s-secrets" (include "notebook-platform.fullname" .)) .Values.secrets.name -}}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.componentName" -}}
{{- printf "%s-%s" (include "notebook-platform.fullname" .root) .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "notebook-platform.labels" -}}
app.kubernetes.io/name: {{ include "notebook-platform.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: notebook-platform
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.global.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "notebook-platform.selectorLabels" -}}
app.kubernetes.io/name: {{ include "notebook-platform.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{- define "notebook-platform.image" -}}
{{- $registry := .root.Values.global.imageRegistry -}}
{{- $repo := .image.repository -}}
{{- $tag := .image.tag | default .root.Chart.AppVersion -}}
{{- $digest := .image.digest | default "" -}}
{{- if $registry -}}
{{- if $digest -}}
{{- printf "%s/%s@%s" $registry $repo $digest -}}
{{- else -}}
{{- printf "%s/%s:%s" $registry $repo $tag -}}
{{- end -}}
{{- else -}}
{{- if $digest -}}
{{- printf "%s@%s" $repo $digest -}}
{{- else -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.secretVolume" -}}
- name: notebook-secrets
  secret:
    secretName: {{ include "notebook-platform.secretName" . }}
    optional: true
    items:
      - key: jwt-private-key.pem
        path: jwt/private.pem
      - key: jwt-public-key.pem
        path: jwt/public.pem
      - key: content-service-jwt-private-key.pem
        path: service-jwt/content-private.pem
      - key: content-service-jwt-public-key.pem
        path: service-jwt/content-public.pem
      - key: identity-service-jwt-private-key.pem
        path: service-jwt/identity-private.pem
      - key: identity-service-jwt-public-key.pem
        path: service-jwt/identity-public.pem
      - key: workspace-service-jwt-private-key.pem
        path: service-jwt/workspace-private.pem
      - key: workspace-service-jwt-public-key.pem
        path: service-jwt/workspace-public.pem
      - key: search-service-jwt-private-key.pem
        path: service-jwt/search-private.pem
      - key: search-service-jwt-public-key.pem
        path: service-jwt/search-public.pem
{{- end -}}

{{- define "notebook-platform.volumeMounts" -}}
- name: tmp
  mountPath: /tmp
- name: notebook-secrets
  mountPath: /etc/notebook/secrets
  readOnly: true
{{- end -}}

{{- define "notebook-platform.retentionDatasourceSecretName" -}}
{{- $cfg := .cfg -}}
{{- $root := .root -}}
{{- if $cfg.existingSecret -}}
{{- $cfg.existingSecret -}}
{{- else -}}
{{- include "notebook-platform.secretName" $root -}}
{{- end -}}
{{- end -}}

{{/*
Optional retention DB env (Faz 110). When enabled=false, only *_RETENTION_DATASOURCE_ENABLED=false
is set — no secret refs, normal runtime datasource unchanged.
*/}}
{{- define "notebook-platform.retentionDatasourceEnv" -}}
{{- $root := .root -}}
{{- $serviceKey := .serviceKey -}}
{{- $envPrefix := .envPrefix -}}
{{- $cfg := index $root.Values.retentionDatasource $serviceKey -}}
- name: {{ $envPrefix }}_RETENTION_DATASOURCE_ENABLED
  value: {{ ternary "true" "false" $cfg.enabled | quote }}
{{- if $cfg.enabled }}
- name: {{ $envPrefix }}_RETENTION_DATASOURCE_URL
  valueFrom:
    secretKeyRef:
      name: {{ include "notebook-platform.retentionDatasourceSecretName" (dict "root" $root "cfg" $cfg) | quote }}
      key: {{ required (printf "retentionDatasource.%s.urlKey is required when enabled" $serviceKey) $cfg.urlKey | quote }}
      optional: false
- name: {{ $envPrefix }}_RETENTION_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "notebook-platform.retentionDatasourceSecretName" (dict "root" $root "cfg" $cfg) | quote }}
      key: {{ required (printf "retentionDatasource.%s.usernameKey is required when enabled" $serviceKey) $cfg.usernameKey | quote }}
      optional: false
- name: {{ $envPrefix }}_RETENTION_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "notebook-platform.retentionDatasourceSecretName" (dict "root" $root "cfg" $cfg) | quote }}
      key: {{ required (printf "retentionDatasource.%s.passwordKey is required when enabled" $serviceKey) $cfg.passwordKey | quote }}
      optional: false
{{- end }}
{{- end -}}

{{- define "notebook-platform.scimDeltaRemoteSecretName" -}}
{{- $cfg := .Values.scimDeltaRemoteFetch.bearerTokenFromSecret -}}
{{- if $cfg.existingSecret -}}
{{- $cfg.existingSecret -}}
{{- else -}}
{{- include "notebook-platform.secretName" . -}}
{{- end -}}
{{- end -}}

{{/*
SCIM delta remote bearer (Faz 118). Injected only when bearerTokenFromSecret.enabled=true.
Never place token literals in ConfigMap or values.yaml.
*/}}
{{- define "notebook-platform.scimDeltaRemoteBearerEnv" -}}
{{- $cfg := .Values.scimDeltaRemoteFetch.bearerTokenFromSecret -}}
{{- if $cfg.enabled -}}
- name: SCIM_DELTA_REMOTE_BEARER_TOKEN
  valueFrom:
    secretKeyRef:
      name: {{ include "notebook-platform.scimDeltaRemoteSecretName" . | quote }}
      key: {{ required "scimDeltaRemoteFetch.bearerTokenFromSecret.secretKey is required when enabled" $cfg.secretKey | quote }}
      optional: {{ ternary "true" "false" ($cfg.optional | default true) }}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.scimDeltaRemoteTokenSecretNameConfig" -}}
{{- if .Values.scimDeltaRemoteFetch.bearerTokenFromSecret.enabled -}}
{{- include "notebook-platform.scimDeltaRemoteSecretName" . -}}
{{- else -}}
{{- .Values.config.scimDeltaRemoteTokenSecretName -}}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.scimDeltaRemoteTokenSecretKeyConfig" -}}
{{- if .Values.scimDeltaRemoteFetch.bearerTokenFromSecret.enabled -}}
{{- .Values.scimDeltaRemoteFetch.bearerTokenFromSecret.secretKey -}}
{{- else -}}
{{- .Values.config.scimDeltaRemoteTokenSecretKey -}}
{{- end -}}
{{- end -}}

{{- define "notebook-platform.otelSecretEnv" -}}
- name: OTEL_EXPORTER_OTLP_HEADERS
  valueFrom:
    secretKeyRef:
      name: {{ include "notebook-platform.secretName" . }}
      key: otel-auth-token
      optional: true
{{- end -}}

{{- define "notebook-platform.secretChecksumSource" -}}
mode: {{ .Values.secrets.mode }}
secretName: {{ include "notebook-platform.secretName" . }}
data:
{{- toYaml .Values.secrets.data | nindent 2 }}
externalSecrets:
{{- toYaml .Values.secrets.externalSecrets | nindent 2 }}
{{- end -}}

{{- define "notebook-platform.podAnnotations" -}}
checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
checksum/secret: {{ include "notebook-platform.secretChecksumSource" . | sha256sum }}
{{- with .Values.global.podAnnotations }}
{{ toYaml . }}
{{- end }}
{{- end -}}
