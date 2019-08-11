#version 310 es
#extension GL_OES_EGL_image_external_essl3 : require
#extension GL_OES_EGL_image_external_essl3 : enable

precision mediump float;

const float PI = 3.14159265;
const vec3 SEPIACOLOR = vec3(0.878f, 0.518f, 0.156f);


float luminance(vec3 color)
{
    vec3 coefficients = vec3(0.2126f, 0.7152f, 0.0722f);
    return dot(coefficients, color);
}

// The function expects the brightness, saturation and contrast values to be in the rgb-xyz members of the BSC vector.
vec3 adjustContrast(vec3 color, vec3 BSC)
{
    vec3 avgLuma = vec3(0.5f, 0.5f, 0.5f);

    vec3 avgLuminance = avgLuma;
    vec3 brightnessColor = color * BSC.r;
    float colorIntensity = luminance(brightnessColor);
    vec3 intensity = vec3(colorIntensity);

    vec3 saturationColor = mix(intensity, brightnessColor, BSC.g);

    vec3 contrastColor = mix(avgLuma, saturationColor, BSC.b);
    return contrastColor;
}

vec3 rotateHue(vec3 color, float angle)
{
    float U = cos(angle/180.0f);
    float W = sin(angle/180.0f);

    vec3 ret;
    ret.r = (.299+.701*U+.168*W)*color.r
    + (.587-.587*U+.330*W)*color.g
    + (.114-.114*U-.497*W)*color.b;

    ret.g = (.299-.299*U-.328*W)*color.r
    + (.587+.413*U+.035*W)*color.g
    + (.114-.114*U+.292*W)*color.b;

    ret.b = (.299-.3*U+1.25*W)*color.r
    + (.587-.588*U-1.05*W)*color.g
    + (.114+.886*U-.203*W)*color.b;

    return ret;
}

vec3 toHSV(vec3 rgb)
{
    float minVal, maxVal, delta;
    float h, s, v;
    minVal = min(min(rgb.r, rgb.g), rgb.b);
    maxVal = max(max(rgb.r, rgb.g), rgb.b);
    v = maxVal;				// v
    delta = maxVal - minVal;
    if(maxVal != 0.0f)
    s = delta / maxVal;		// s
    else {
        // r = g = b = 0		// s = 0, v is undefined
        s = 0.0f;
        h = -1.0f;
        return vec3(h, s, v);
    }

    if(rgb.r == maxVal)
    h = (rgb.g - rgb.b) / delta;		// between yellow & magenta
    else if(rgb.g == maxVal)
    h = 2.0f + (rgb.b - rgb.r) / delta;	// between cyan & yellow
    else
    h = 4.0f + (rgb.r - rgb.g) / delta;	// between magenta & cyan

    h *= 60.0f;				// degrees
    h += float(h < 0.0f) * 360.0f;

    return vec3(h, s, v);
}


vec3 toRGB(vec3 hsv)
{
    int i;
    float f, p, q, t;
    float r, g, b;
    if(hsv.g == 0.0f) {
        // achromatic (grey)
        return vec3(hsv.b, hsv.b, hsv.b);
    }
    hsv.x /= 60.0f;			// sector 0 to 5
    i = int(floor(hsv.x));
    f = hsv.x - float(i);			// factorial part of h
    p = hsv.z * (1.0f - hsv.y);
    q = hsv.z * (1.0f - hsv.y * f);
    t = hsv.z * (1.0f - hsv.y * (1.0f - f));

    // Unrolling the switch case for fast math
    r = float(i == 0) * hsv.z + float(i == 1) * q     + float(i == 2) * p     + float(i == 3) * p     + float(i == 4) * t     + float(i == 5) * hsv.z;
    g = float(i == 0) * t     + float(i == 1) * hsv.z + float(i == 2) * hsv.z + float(i == 3) * q     + float(i == 4) * p     + float(i == 5) * p;
    b = float(i == 0) * p     + float(i == 1) * p     + float(i == 2) * t     + float(i == 3) * hsv.z + float(i == 4) * hsv.z + float(i == 5) * q;

    return vec3(r, g, b);
}

vec2 pixelate(vec2 position, vec2 factor)
{
    vec2 result = position.xy;

    if(factor.x <= 0.0f || factor.y <= 0.0f) {
        return result;
    }

    result.x -= mod(result.x, factor.x);
    result.y -= mod(result.y, factor.y);

    result.x = min(max(result.x, 0.0f), 1.0f);
    result.y = min(max(result.y, 0.0f), 1.0f);

    return result;
}

vec3 solarize(vec3 texColor)
{
    float red = 1.0f - 4.0f * texColor.r + 4.0f * texColor.r * texColor.r;
    float green = 1.0f - 4.0f * texColor.g + 4.0f * texColor.g * texColor.g;
    float blue = 1.0f - 4.0f * texColor.b + 4.0f * texColor.b * texColor.b;
    red = min(max(red, 0.0f), 1.0f);
    green = min(max(green, 0.0f), 1.0f);
    blue = min(max(blue, 0.0f), 1.0f);
    return vec3(red, green, blue);
}