function switchTab(btn, target) {
    [].forEach.call(document.querySelectorAll('#analysisTab .nav-link'), function(t) { t.classList.remove('active'); });
    [].forEach.call(document.querySelectorAll('#analysisTabContent > .tab-pane'), function(p) { p.classList.remove('show', 'active'); });
    btn.classList.add('active');
    var pane = document.querySelector(target);
    if (pane) pane.classList.add('show', 'active');
}

function switchSubTab(btn, target) {
    var group = btn.closest('.sub-tab-group');
    var contentDiv = group.nextElementSibling;
    [].forEach.call(group.querySelectorAll('.nav-link'), function(t) { t.classList.remove('active'); });
    if (contentDiv) { [].forEach.call(contentDiv.querySelectorAll('.tab-pane'), function(p) { p.classList.remove('show', 'active'); }); }
    btn.classList.add('active');
    var pane = document.querySelector(target);
    if (pane) pane.classList.add('show', 'active');
}
