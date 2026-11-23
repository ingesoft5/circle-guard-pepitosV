{{- define "circleguard.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: circleguard
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
circleguard.io/environment: {{ .root.Values.environment }}
{{- end }}

{{- define "circleguard.selector" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{/* Image of an application workload, or the full reference of a middleware image. */}}
{{- define "circleguard.image" -}}
{{- if .spec.externalImage -}}
{{ .spec.externalImage }}
{{- else -}}
{{- $v := .root.Values.image -}}
{{- if not $v.registry }}{{ fail "image.registry is required (--set image.registry=<host:port>)" }}{{ end -}}
{{- if not $v.tag }}{{ fail "image.tag is required (--set image.tag=<version>)" }}{{ end -}}
{{ $v.registry }}/circleguard/{{ .spec.image }}:{{ $v.tag }}
{{- end -}}
{{- end }}
