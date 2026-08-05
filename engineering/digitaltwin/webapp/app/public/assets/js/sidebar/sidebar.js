$(document).ready(function () {
    $(document).on('click', '.has-sub > a', function (e) {
        e.preventDefault();
        let parent = $(this).parent();
        parent.toggleClass('open');

        let subMenu = parent.find('.slide-menu');
        if (parent.hasClass('open')) {
            subMenu.slideDown(200);
        } else {
            subMenu.slideUp(200);
        }
    });
});