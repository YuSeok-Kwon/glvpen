(function() {
    var countdownSpan = document.getElementById('countdownText');
    if (countdownSpan) {
        var matchTime = new Date(countdownSpan.dataset.datetime);
        function updateCountdown() {
            var diffMs = matchTime - new Date();
            if (!(diffMs > 0)) { countdownSpan.textContent = "곧 시작합니다!"; return; }
            var s = Math.floor(diffMs / 1000);
            var d = Math.floor(s / 86400), h = Math.floor((s % 86400) / 3600),
                m = Math.floor((s % 3600) / 60), sec = s % 60;
            var p = [];
            if (d > 0) p.push(d + '일');
            if (h > 0 || d > 0) p.push(h + '시간');
            if (m > 0 || h > 0 || d > 0) p.push(m + '분');
            p.push(sec + '초');
            countdownSpan.textContent = '(' + p.join(' ') + ')';
        }
        updateCountdown();
        setInterval(updateCountdown, 1000);
    }
})();
