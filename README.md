# Vision Assist 👁️📱
An AI-powered Android application designed to assist visually impaired users with real-time object detection, text recognition, and audio feedback — all functioning offline.

## 🌟 Features

- **Real-time Object Detection** using MobileNet SSD
- **Text Recognition (OCR)** using Google ML Kit
- **Text-to-Speech Feedback** for both objects and text
- **Custom Distance Estimation** using bounding box height and camera parameters
- **Gesture-Based Navigation** (Swipe left/right to switch between modes)
- **Emergency Contact Button** (Long-press to call a pre-saved number)
- **TalkBack Accessibility Support** (Single tap to speak, long press to act)
- **Offline Functionality** — No internet required for main features

## 🛠️ Built With

- **Kotlin** – Main programming language
- **Android Studio** – Development environment
- **TensorFlow Lite** – On-device object detection
- **Google ML Kit** – On-device OCR (Text Recognition)
- **Camera2 API** – Real-time camera feed processing
- **Text-to-Speech API** – Converts output to spoken feedback


## 📦 How to Run

	1. Clone the repository:```bash
   git clone https://github.com/yourusername/Vision-Assist.git
	2.	Open in Android Studio.
	3.	Connect your Android device (API 24+).
	4.	Run the app.
 Make sure you enable camera and microphone permissions on your device.
