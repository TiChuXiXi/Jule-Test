package com.example.pdfapplication.dto;

public class ImageResponse {
    private String imageName;
    private String imageType; // e.g., "png"
    private String base64Image;

    public ImageResponse(String imageName, String imageType, String base64Image) {
        this.imageName = imageName;
        this.imageType = imageType;
        this.base64Image = base64Image;
    }

    // Getters and Setters
    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }
}
