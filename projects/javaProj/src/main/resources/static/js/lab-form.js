document.addEventListener('DOMContentLoaded', () => {
  const course = document.querySelector('#lab-course');
  const groups = document.querySelector('#lab-groups');
  if (course && groups) {
    const filterGroups = () => {
      for (const option of groups.options) {
        option.hidden = Boolean(course.value) && option.dataset.course !== course.value;
        if (option.hidden) option.selected = false;
      }
    };
    course.addEventListener('change', filterGroups);
    filterGroups();
  }
  const secure = document.querySelector('#secure-mode');
  const camera = document.querySelector('#camera-monitoring');
  if (secure && camera) {
    const updateCameraOption = () => {
      camera.disabled = !secure.checked;
      if (!secure.checked) camera.checked = false;
    };
    secure.addEventListener('change', updateCameraOption);
    updateCameraOption();
  }
});
