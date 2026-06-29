# Generates patches/rethinkingvoxels.irlights by splicing the IRLite bodies
# VERBATIM out of Shadres/Modification/RethinkingVoxels (so the patch reproduces
# the working tree byte-for-byte). Anchors are unique literals captured from the
# PRISTINE pack — verified before generation.
# RethinkingVoxels is a Complementary fork: forward DoLighting hook is identical,
# but the composite seam is program/composite.glsl (no composite1), the VL buffer
# is colortex15 (colortex0-14 are taken), the deferred2 toggles + size.buffer go
# in one block before "# Miscellaneous", and lang/properties anchors differ.
# Validate: javac harness applies the patch to the pristine pack and
# `git -c core.autocrlf=false diff --no-index --ignore-cr-at-eol <out> <Modification>` empty.

$ErrorActionPreference = "Stop"
$repo = "C:\Users\Qualet\Documents\Project\Minecraft\BBS\bbs-irlights-addon"
$mod  = "$repo\Shadres\Modification\RethinkingVoxels\shaders"
$out  = "$repo\patches\rethinkingvoxels.irlights"

function Lines($path) { [IO.File]::ReadAllLines($path) }
function FileText($path) { [IO.File]::ReadAllText($path).Replace("`r`n", "`n") }
function IndexOfLine($lines, $text) {
    for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found: $text"
}
function IndexOfLineAfter($lines, $text, $from) {
    for ($i = $from; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $text) { return $i } }
    throw "line not found after ${from}: $text"
}

# ---- extract +file bodies from Modification (verbatim) ----
$libText = FileText "$mod\lib\irlite\irlite_lights.glsl"
$d2Text  = FileText "$mod\program\deferred2.glsl"
$wrapTexts = @{}
foreach ($w in @("world0\deferred2.fsh","world0\deferred2.vsh","world1\deferred2.fsh","world1\deferred2.vsh","world-1\deferred2.fsh","world-1\deferred2.vsh")) {
    $wrapTexts[$w] = FileText "$mod\$w"
}

# ---- mainLighting.glsl (forward diffuse + specular) ----
$ml = Lines "$mod\lib\lighting\mainLighting.glsl"
$S = IndexOfLine $ml '    finalDiffuse = sqrt(max(finalDiffuse, vec3(0.0))); // sqrt() for a bit more realistic light mix, max() to prevent NaNs'
$Nan = IndexOfLineAfter $ml '    if (any(isnan(finalDiffuse))) finalDiffuse = vec3(0.0);' $S
$mlBlock = $ml[($S + 1)..($Nan - 1)]        # leading blank + IRLite diffuse block (isnan line follows directly)
if ($mlBlock[0] -ne '') { throw "expected leading blank in mlBlock" }
$H = IndexOfLine $ml '    color.rgb += lightHighlight;'
$D = IndexOfLineAfter $ml '    color.rgb *= pow2(1.0 - darknessLightFactor);' $H
$mlSpec = $ml[($H + 1)..($D - 1)]           # the 3-line spec add, no surrounding blanks

# ---- composite.glsl (include + outline before pow, VL after pow) ----
$c = Lines "$mod\program\composite.glsl"
$CF = IndexOfLine $c '#include "/lib/atmospherics/fog/caveFactor.glsl"'
$II = IndexOfLineAfter $c '#include "/lib/irlite/irlite_lights.glsl"' $CF
$cInc = $c[($CF + 1)..$II]                   # [blank, irlite include]
if ($cInc[0] -ne '') { throw "expected blank before composite irlite include" }
$P = IndexOfLine $c '    color = pow(color, vec3(2.2));'
$OStart = IndexOfLine $c '    #if defined IRLITE_ACTIVE && defined IRLITE_OUTLINE'
if ($c[$P - 1] -ne '') { throw "expected blank before pow line" }
$cOutline = $c[$OStart..($P - 1)]            # outline block + trailing blank (before-op needs the trailing \n)
$L = IndexOfLineAfter $c '    #ifdef LIGHTSHAFTS_ACTIVE' $P
if ($c[$L - 1] -ne '') { throw "expected blank before LIGHTSHAFTS" }
$cVl = $c[($P + 1)..($L - 2)]                # leading blank + VL block; drop the trailing blank
if ($cVl[0] -ne '') { throw "expected leading blank in cVl" }

# ---- pipelineSettings.glsl (colortex15 format inside the comment block) ----
$ps = Lines "$mod\lib\pipelineSettings.glsl"
$F = IndexOfLine $ps 'const int colortex15Format= RGBA16F;        //IRLite reduced-res volumetric (added deferred2 pass)'
if ($ps[$F - 1] -ne 'const int colortex14Format= RGBA16F;        //specular lighting') { throw "colortex15Format must directly follow colortex14Format" }
$psFormat = @($ps[$F])

# ---- shaders.properties ----
$pr = Lines "$mod\shaders.properties"
$T = IndexOfLine $pr '# IRLite reduced-resolution volumetric pass (added deferred2 -> colortex15)'
$prBlock = $pr[$T..($T + 4)]                 # comment + 3 toggles + size.buffer.colortex15
if ($prBlock[4] -ne '    size.buffer.colortex15 = IRLITE_VL_RESOLUTION IRLITE_VL_RESOLUTION') { throw "toggle/size block tail unexpected" }
if ($pr[$T + 5] -ne '') { throw "expected blank after the toggle/size block" }
if ($pr[$T + 6] -ne '# Miscellaneous') { throw "expected # Miscellaneous after the toggle/size block" }
$prBlock += ''                               # trailing blank (before-op reproduces the blank above the anchor)
$X = IndexOfLine $pr '        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE TEXTURE_RES'
$O = -1; for ($i = $X + 1; $i -lt $pr.Count; $i++) { if ($pr[$i].StartsWith('    screen.OTHER_SETTINGS=')) { $O = $i; break } }
if ($O -lt 0) { throw "OTHER_SETTINGS not found" }
$propsScreens = $pr[($X + 1)..($O - 1)]      # the 6 IRLITE screen lines
$slLine = $pr | Where-Object { $_ -match 'WATER_BUMP_INTERACTIVE TEXTURE_RES IRLITE_INTENSITY' }
if (@($slLine).Count -ne 1) { throw "sliders IRLITE line not unique" }
$slIdx = $slLine.IndexOf('WATER_BUMP_INTERACTIVE TEXTURE_RES')
if ($slIdx -lt 0) { throw "sliders tail anchor not found" }
$slBody = $slLine.Substring($slIdx)
if (-not $slBody.EndsWith('IRLITE_OUTLINE_FRESNEL_POWER')) { throw "sliders body tail unexpected" }
if ($slBody -notmatch 'IRLITE_VL_SHADOW_STRIDE') { throw "sliders body missing IRLITE_VL_SHADOW_STRIDE" }

# ---- lang/en_US.lang (append block) ----
$lg = Lines "$mod\lang\en_US.lang"
$Y = IndexOfLine $lg 'option.RESIN_COL_B=Blue'
$langTail = $lg[($Y + 1)..($lg.Count - 1)]   # 2 leading blanks + the whole IRLite block
while ($langTail[-1] -eq '') { $langTail = $langTail[0..($langTail.Count - 2)] }
if ($langTail.Count -lt 10) { throw "lang tail too short" }

# ---- assemble the patch ----
$sb = New-Object System.Text.StringBuilder
function Emit($s) { [void]$sb.Append($s).Append("`n") }
function EmitBody($lines) { Emit '<<<'; foreach ($l in $lines) { Emit $l }; Emit '>>>' }
function EmitFile($relPath, $text) {
    Emit "+file $relPath"
    Emit '<<<'
    if ($text.EndsWith("`n")) {
        [void]$sb.Append($text)
        Emit ''                              # blank last body line -> the applier emits the trailing \n
    } else {
        [void]$sb.Append($text).Append("`n")
    }
    Emit '>>>'
}

Emit '# IRLite point + spot lights for RethinkingVoxels (a Complementary fork, by gri573 / EminGT base).'
Emit '@name    RethinkingVoxels lights'
Emit '@target  RethinkingVoxels'
Emit '@irlite  1'
Emit '@marker  IRLITE'
Emit ''
Emit '# --- light SSBO, options and shading functions (surface + outline + volumetric) ---'
EmitFile 'shaders/lib/irlite/irlite_lights.glsl' $libText
Emit ''
Emit '# --- the added reduced-resolution volumetric pass (writes colortex15) ---'
EmitFile 'shaders/program/deferred2.glsl' $d2Text
EmitFile 'shaders/world0/deferred2.fsh' $wrapTexts['world0\deferred2.fsh']
EmitFile 'shaders/world0/deferred2.vsh' $wrapTexts['world0\deferred2.vsh']
EmitFile 'shaders/world1/deferred2.fsh' $wrapTexts['world1\deferred2.fsh']
EmitFile 'shaders/world1/deferred2.vsh' $wrapTexts['world1\deferred2.vsh']
EmitFile 'shaders/world-1/deferred2.fsh' $wrapTexts['world-1\deferred2.fsh']
EmitFile 'shaders/world-1/deferred2.vsh' $wrapTexts['world-1\deferred2.vsh']
Emit ''
Emit '# --- forward diffuse + specular in DoLighting (anchors identical to Complementary) ---'
Emit '@file shaders/lib/lighting/mainLighting.glsl'
Emit 'after "#include \"/lib/lighting/ggx.glsl\""'
EmitBody @('#define IRLITE_SURFACE_PASS', '#include "/lib/irlite/irlite_lights.glsl"')
Emit 'after "    finalDiffuse = sqrt(max(finalDiffuse, vec3(0.0))); // sqrt() for a bit more realistic light mix, max() to prevent NaNs"'
EmitBody $mlBlock
Emit 'after "    color.rgb += lightHighlight;"'
EmitBody $mlSpec
Emit ''
Emit '# --- rim outline (before pow 2.2, gamma) + volumetric upsample (after pow 2.2, linear) ---'
Emit '@file shaders/program/composite.glsl'
Emit 'after "#include \"/lib/atmospherics/fog/caveFactor.glsl\""'
EmitBody $cInc
Emit 'before "    color = pow(color, vec3(2.2));"'
EmitBody $cOutline
Emit 'after "    color = pow(color, vec3(2.2));"'
EmitBody $cVl
Emit ''
Emit '# --- the reduced-res VL buffer format (the pack declares formats inside this comment block) ---'
Emit '@file shaders/lib/pipelineSettings.glsl'
Emit 'after "const int colortex14Format= RGBA16F;        //specular lighting"'
EmitBody $psFormat
Emit ''
Emit '# --- deferred2 program toggle + buffer size, settings screens + sliders ---'
Emit '@file shaders/shaders.properties'
Emit 'before "# Miscellaneous"'
EmitBody $prBlock
Emit 'replace "VANILLAAO_I PLAYER_SHADOW"'
EmitBody @('VANILLAAO_I PLAYER_SHADOW [IRLITE_SETTINGS]')
Emit 'after "        screen.PIXELATED_LIGHTING_SETTINGS=<empty> <empty> PIXELATED_SHADOWS PIXELATED_BLOCKLIGHT PIXELATED_AO PIXEL_SCALE TEXTURE_RES"'
EmitBody $propsScreens
Emit 'replace "WATER_BUMP_INTERACTIVE TEXTURE_RES"'
EmitBody @($slBody)
Emit ''
Emit '# --- option labels + tooltips ---'
Emit '@file shaders/lang/en_US.lang'
Emit 'after "option.RESIN_COL_B=Blue"'
EmitBody $langTail

[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("written {0} ({1} lines)" -f $out, ($sb.ToString().Split("`n").Count))
