{{/*
Chart name
*/}}
{{- define "project-controller.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified name
*/}}
{{- define "project-controller.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Namespace
*/}}
{{- define "project-controller.namespace" -}}
{{- .Release.Namespace }}
{{- end }}

{{/*
Chart label value
*/}}
{{- define "project-controller.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "project-controller.labels" -}}
app: {{ include "project-controller.name" . }}
helm.sh/chart: {{ include "project-controller.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "project-controller.selectorLabels" -}}
app: {{ include "project-controller.name" . }}
{{- end }}

{{/*
Webhook clientConfig helper
Usage: {{ include "project-controller.webhookClientConfig" (dict "root" . "path" "/api/v1/admissionreviews") }}
*/}}
{{- define "project-controller.webhookClientConfig" -}}
caBundle: {{ .root.Values.webhook.caBundle }}
service:
  namespace: {{ include "project-controller.namespace" .root }}
  name: {{ include "project-controller.fullname" .root }}
  path: {{ .path }}
  port: {{ .root.Values.service.port }}
{{- end }}

{{/*
Webhook excluded namespace selector
*/}}
{{- define "project-controller.webhookExcludedNamespaceSelector" -}}
matchExpressions:
  - key: kubernetes.io/metadata.name
    operator: NotIn
    values:
      {{- range .Values.webhook.excludedNamespaces }}
      - {{ . }}
      {{- end }}
{{- end }}
