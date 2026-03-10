
const API_BASE = "http://localhost:8080";

async function analyzeEmail(){
const emailContent = "Interview scheduled tomorrow 10AM";

const res = await fetch("http://localhost:8080/api/analyze-email",{
method:"POST",
headers:{"Content-Type":"application/json"},
body:JSON.stringify({content:emailContent})
});
const data = await res.json();

document.querySelector("#viewer p").innerText = data.summary;
document.querySelector("textarea").value = data.reply;
}


