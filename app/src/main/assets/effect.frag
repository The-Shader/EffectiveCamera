uniform samplerExternalOES cameraTexture;

uniform float brightness;
uniform float saturation;
uniform float contrast;
uniform float sepia;
uniform float grayscale;
uniform float invert;
uniform float hueRotation;
uniform float opacity;
uniform float pixelFactor;

in vec2 textureCoordinates;
out vec4 fragColor;

void main()
{
    vec2 texSize = vec2(textureSize(cameraTexture, 0));

    float horizontal = texSize.x / texSize.y;
    float vertical = texSize.y / texSize.x;

    float factorX = pixelFactor / texSize.x * horizontal;

    float factorY = pixelFactor / texSize.y * vertical;

    vec2 newCoords = pixelate(textureCoordinates, vec2(factorX, factorY));

    vec4 texColor = texture(cameraTexture, newCoords.xy);

    float luma = luminance(texColor.rgb);

    vec3 adjustedColor = adjustContrast(texColor.rgb, vec3(brightness, saturation, contrast));

    adjustedColor = mix(adjustedColor, luma * SEPIACOLOR, sepia);

    adjustedColor = mix(adjustedColor, vec3(luma), grayscale);

    if (hueRotation != 0.0f) {
        adjustedColor = rotateHue(adjustedColor, hueRotation);
    }

    vec3 negativeColor = vec3(1.0f) - adjustedColor;

    adjustedColor = mix(adjustedColor, negativeColor, invert);

    fragColor = vec4(adjustedColor, 1.0f);
}