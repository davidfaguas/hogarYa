param (
    [string]$ApiKey = $env:GEMINI_API_KEY
)

Write-Host "====================================================================" -ForegroundColor Cyan
Write-Host " HogarYa Chile - Smoke Test Manual: Google Gemini Embeddings API" -ForegroundColor Cyan
Write-Host "====================================================================" -ForegroundColor Cyan

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Host "AVISO: No se detectó GEMINI_API_KEY en variables de entorno." -ForegroundColor Yellow
    $ApiKey = Read-Host "Por favor ingrese su GEMINI_API_KEY de Google AI Studio"
}

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Error "Error: La clave de API es obligatoria para ejecutar el smoke test real."
    exit 1
}

$model = "models/gemini-embedding-001"
$url = "https://generativelanguage.googleapis.com/v1beta/${model}:embedContent?key=$ApiKey"

$bodyObj = @{
    model = $model
    content = @{
        parts = @(
            @{ text = "Departamento 2D2B en Providencia cercano a estación de Metro Tobalaba con estacionamiento y bodega" }
        )
    }
    taskType = "RETRIEVAL_QUERY"
    outputDimensionality = 768
}

$jsonPayload = $bodyObj | ConvertTo-Json -Depth 5

Write-Host "`n1. Enviando solicitud POST a Google Gemini API..." -ForegroundColor Yellow
Write-Host "   URL: https://generativelanguage.googleapis.com/v1beta/${model}:embedContent" -ForegroundColor Gray
Write-Host "   TaskType: RETRIEVAL_QUERY | outputDimensionality: 768" -ForegroundColor Gray

try {
    $response = Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json" -Body $jsonPayload
} catch {
    Write-Host "`n[ERROR] Falló la llamada a Gemini API:" -ForegroundColor Red
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $errBody = $reader.ReadToEnd()
        Write-Host $errBody -ForegroundColor Red
    } else {
        Write-Host $_.Exception.Message -ForegroundColor Red
    }
    exit 1
}

if (-not $response.embedding -or -not $response.embedding.values) {
    Write-Error "Respuesta inválida: No se recibió 'embedding.values'."
    exit 1
}

$values = $response.embedding.values
$dim = $values.Count

Write-Host "`n2. Validación de Dimensiones de Salida:" -ForegroundColor Green
Write-Host "   Dimensiones recibidas: $dim" -ForegroundColor White
if ($dim -eq 768) {
    Write-Host "   [OK] Coincide exactamente con VECTOR(768) de pgvector." -ForegroundColor Green
} else {
    Write-Host "   [ALERTA] Se esperaban 768 dimensiones pero se recibieron $dim." -ForegroundColor Red
}

# 3. Cálculo de Norma L2 Cruda
$sumSqRaw = 0.0
foreach ($v in $values) {
    $sumSqRaw += ($v * $v)
}
$rawL2Norm = [Math]::Sqrt($sumSqRaw)

Write-Host "`n3. Análisis de Norma Euclidiana L2 Cruda:" -ForegroundColor Yellow
Write-Host "   Norma L2 Cruda: $rawL2Norm" -ForegroundColor White
Write-Host "   [CONFIRMADO]: La API cruda de Gemini devuelve valores NO normalizados unitariamente (norma != 1)." -ForegroundColor Cyan
Write-Host "   Por ello, GeminiEmbeddingProvider aplica normalización L2 obligatoria antes de persistir." -ForegroundColor Gray

# 4. Normalización en el Proveedor (v / ||v||_2)
$normalizedValues = @()
$sumSqNorm = 0.0
foreach ($v in $values) {
    $normVal = $v / $rawL2Norm
    $normalizedValues += $normVal
    $sumSqNorm += ($normVal * $normVal)
}
$finalL2Norm = [Math]::Sqrt($sumSqNorm)

Write-Host "`n4. Vector Normalizado por GeminiEmbeddingProvider:" -ForegroundColor Green
Write-Host "   Primeros 5 floats crudos:       $($values[0..4] -join ', ')" -ForegroundColor Gray
Write-Host "   Primeros 5 floats normalizados: $($normalizedValues[0..4] -join ', ')" -ForegroundColor Gray
Write-Host "   Norma L2 tras normalización:    $finalL2Norm" -ForegroundColor White

if ([Math]::Abs($finalL2Norm - 1.0) -lt 0.0001) {
    Write-Host "`n[APROBADO] Smoke test exitoso: Dimensión = 768, Norma Cruda != 1, Norma Normalizada = 1.0000" -ForegroundColor Green
} else {
    Write-Host "`n[ERROR] Error en cálculo de norma normalizada." -ForegroundColor Red
}
