function init() {
    const button = document.getElementById('migrationSubmit');
    button.addEventListener('click', function() {
        document.getElementById("loader").style.display = "block"; //show loader
        button.style.display="none";  							   //hide submit button
        const autoSchedule = document.querySelector('input[name="autoSchedule"]:checked').value;;
        const startIndex = document.getElementById('startIndexMigration')?.value;
        const batchSize = document.getElementById('batchSizeMigration')?.value;
        const sourcePath = document.getElementById('sourcePathMigration')?.value;
        const destinationPath = document.getElementById('destinationPathMigration')?.value;
        fetchAPICall(autoSchedule, startIndex, batchSize, sourcePath, destinationPath);
    });

    const popup = document.getElementById("popup");
    const overlay = document.querySelector('.overlay');
    const runNextBatchBtn = document.getElementById("runNextBatchBtn");
    runNextBatchBtn.addEventListener('click', function() {
        console.log("Schedule Next Fired");
        document.getElementById("loader").style.display = "block"; //show loader
        button.style.display="none";                               //hide submit button
        clearPopupContent();
        overlay.style.display = "none";
        popup.classList.add('hidden');
        const batchSize = document.getElementById('batchSizeMigration')?.value;
        const previousStartIndex = document.getElementById('previousStartIndex')?.value;
        const startIndex = parseInt(previousStartIndex) + parseInt(batchSize);
        const sourcePath = document.getElementById('sourcePathMigration')?.value;
        const destinationPath = document.getElementById('destinationPathMigration')?.value;
        fetchAPICall(false, startIndex, batchSize, sourcePath, destinationPath);
    });

    const closePopupBtn = document.getElementById('closePopupBtn');
    closePopupBtn.addEventListener('click', function() {
        clearPopupContent();
        overlay.style.display = "none";
        popup.classList.add('hidden');
    });

	const autoScheduleTrue = document.querySelector('input[name="autoSchedule"][value="true"]');
    const autoScheduleFalse = document.querySelector('input[name="autoSchedule"][value="false"]');
	const startIndexField = document.getElementById('startIndexMigration');

    autoScheduleTrue.addEventListener('change', updateStartIndexField);
    autoScheduleFalse.addEventListener('change', updateStartIndexField);

	function updateStartIndexField() {
        if (autoScheduleTrue.checked) {
        	startIndexField.disabled = true;
    	} else {
        	startIndexField.disabled = false;
    	}
    }

}

function clearPopupContent() {
    const table = document.querySelector('.popup-table');
    table?.remove();
    const errorElement = document.querySelector(".errorElement");
    errorElement?.remove();
}

function showPopupWithData(responseJSON, autoSchedule) {
    const overlay = document.querySelector('.overlay');
    const popup = document.getElementById("popup");
    const runNextBatchBtn = document.getElementById("runNextBatchBtn");
    if (responseJSON.hasOwnProperty('Error')) {
        const errorElement = document.createElement("div");
        errorElement.classList.add("errorElement");
        errorElement.innerText = "Error : " + responseJSON["Error"];
        popup.insertBefore(errorElement, popup.firstChild);
        runNextBatchBtn.classList.add("hidden");
    } else {
        runNextBatchBtn.classList.remove("hidden");
        const table = createTableFromResponseJson(responseJSON, autoSchedule);
        popup.insertBefore(table, popup.firstChild);
    }
        document.getElementById("loader").style.display = "none";
        const button = document.getElementById('migrationSubmit');
        button.style.display="block";
    overlay.style.display = "block";
    popup.classList.remove('hidden');
}

function createTableFromResponseJson(responseJSON, autoSchedule) {
    const table = document.createElement('table');
    table.classList.add("popup-table");

    const headerRow = table.insertRow();
    const headerKeyCell = headerRow.insertCell(0);
    const headerValueCell = headerRow.insertCell(1);

    const runNextBatchBtn = document.getElementById("runNextBatchBtn");
    if (autoSchedule === "true") {
        runNextBatchBtn.classList.add("hidden");
        headerKeyCell.textContent = 'Indexes';
        headerValueCell.textContent = 'Job ID';
    } else {
        runNextBatchBtn.classList.remove("hidden");
        headerKeyCell.textContent = 'Index - Asset';
        headerValueCell.textContent = 'Exit Code / Status';
    }


    for (const key in responseJSON) {
        if (responseJSON.hasOwnProperty(key)) {
            const value = responseJSON[key];

            const row = table.insertRow();
            const keyCell = row.insertCell(0);
            const valueCell = row.insertCell(1);

            keyCell.textContent = key;
            valueCell.textContent = value;
        }
    }
    return table;
}

async function fetchAPICall(autoSchedule, startIndex, batchSize, sourcePath, destinationPath) {
    try {
        let servletPath = document.getElementById('dataSourceMigration').value;
        const response = await fetch(servletPath + '?startIndex=' + startIndex + '&batchSize=' + batchSize +
        '&sourcePath=' + sourcePath + '&destinationPath=' + destinationPath + '&autoSchedule=' + autoSchedule);
        if (response.ok) {
            const responseJSON = await response.json();
            const previousStartIndex = document.getElementById('previousStartIndex');
            previousStartIndex.value = startIndex;
            showPopupWithData(responseJSON, autoSchedule);
        } else {
            console.error('Error fetching data:', response.status);
        }
    } catch (error) {
        console.error('An error occurred:', error);
    }
}

document.addEventListener("DOMContentLoaded", init);