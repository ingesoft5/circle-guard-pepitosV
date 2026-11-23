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

{{/*
"true" when Flagger runs the canary of .Values.canary.service: canary and mesh enabled and the
cluster has the Flagger and Gateway API CRDs. Flagger then owns that workload's Services.
*/}}
{{- define "circleguard.canaryOn" -}}
{{- $v := .Values -}}
{{- if and $v.canary.enabled $v.mesh.enabled (.Capabilities.APIVersions.Has "flagger.app/v1beta1/Canary") (.Capabilities.APIVersions.Has "gateway.networking.k8s.io/v1/HTTPRoute") -}}
true
{{- end -}}
{{- end }}
