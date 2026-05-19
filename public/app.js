console.log("Hello from app.js served by the NIO static server.");

const button = document.querySelector("#ping-button");
const message = document.querySelector("#message");

button.addEventListener("click", () => {
    message.textContent = "app.js is loaded and running.";
});
