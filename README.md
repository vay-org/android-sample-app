# Vay Android Sample App

A single screen app that demonstrates how to integrate [Vay's API][1].

The app features working code that...
- Extracts camera frames using the [CameraX API][2].
- Converts and rotates these frames correctly before sending.
- Handles communication with our server.
- Visualizes a skeleton from the received key points.
- Counts correct reps and displays one (of potentially multiple) correction for wrong reps.

##### Note that our server address has been omitted, add it to the 'url' variable in the MainActivity, where it reads "Insert correct url here". 

Feel free to change the 'exerciseKey' variable in order to test other exercises.

[1]: https://api.docs.vay.ai/
[2]: https://developer.android.com/training/camerax