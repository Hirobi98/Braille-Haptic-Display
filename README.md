# Braille-Haptic-Display

Braille Haptic Display with ESP32
📖 Project Overview
This project demonstrates how to capture text from an image on a mobile device, convert it into Braille, and render it on a haptic display using ESP32 and servo/relay mechanisms. The goal is to create an assistive technology prototype that helps visually impaired users feel text through raised dots.

🔄 Workflow
->Image Capture (Mobile App) -> 
              |
Android app captures an image using the camera.
              |
OCR (Google ML Kit) extracts text from the image.
              |
Text to Braille Conversion
              |
Each character is mapped to a 3×2 Braille matrix.
              |
The matrix is encoded into JSON format.
              |
Data Transfer to ESP32
              |
Mobile app sends the Braille matrix via HTTP POST request.
              |
ESP32 runs a lightweight web server to receive the data.
              |
Haptic Rendering
              |
ESP32 controls servo motors or relay coils.
              |
Each motor/coil raises or lowers a dot to form Braille characters.

🛠️# Hardware Requirements
ESP32 Dev Module

Servo motors (SG90 or MG995) / Relay coils

Power supply (5V for servos, regulated for ESP32)

Breadboard, jumper wires, and mounting board for Braille dots

💻 Software Requirements
Arduino IDE with ESP32 board support

Android Studio (for mobile app development)

Google ML Kit (OCR)

Arduino libraries: WiFi.h, WebServer.h, Servo.h
