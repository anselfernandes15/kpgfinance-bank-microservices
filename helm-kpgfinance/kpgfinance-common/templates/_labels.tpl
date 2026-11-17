{{/*
Labels shared by every kpgfinance resource. Pass the root context (.).
Never use these in a selector - selectors are immutable and helm.sh/chart
changes on every chart version bump.
*/}}
{{- define "kpgfinance.commonLabels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: {{ .Values.global.partOf | default "kpgfinance" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end -}}

{{/*
Labels for a component's Deployment/Service metadata.
Usage: {{ include "kpgfinance.labels" (dict "ctx" $ "name" "redis") }}
*/}}
{{- define "kpgfinance.labels" -}}
app: {{ .name }}
app.kubernetes.io/name: {{ .name }}
{{ include "kpgfinance.commonLabels" .ctx }}
{{- end -}}

{{/*
Labels for a component's pod template. Leaves out helm.sh/chart and
managed-by so a chart version bump alone doesn't roll every pod.
Usage: {{ include "kpgfinance.podLabels" (dict "ctx" $ "name" "redis") }}
*/}}
{{- define "kpgfinance.podLabels" -}}
app: {{ .name }}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .ctx.Release.Name }}
app.kubernetes.io/part-of: {{ .ctx.Values.global.partOf | default "kpgfinance" }}
{{- end -}}