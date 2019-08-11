#version 310 es

uniform mat4 textureTransformMatrix;
in vec2 position;
in vec2 texCoords;
out vec2 textureCoordinates;

void main()
{
    textureCoordinates = (textureTransformMatrix * vec4(texCoords.x, texCoords.y, 0, 1)).xy;
    gl_Position = vec4(position.x, position.y, 0.0, 1.0);
}
