package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ConstantTensorJsonValidator.InvalidConstantTensor;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.io.FileNotFoundException;
import java.io.Reader;

/**
 * RankingConstantsValidator validates all constant tensors (ranking constants) bundled with an application package
 *
 * @author Vegard Sjonfjell
 */

public class RankingConstantsValidator extends Validator {

    private static class ExceptionMessageCollector {
        public String combinedMessage;
        public boolean exceptionsOccurred = false;

        public ExceptionMessageCollector(String messagePrelude) {
            this.combinedMessage = messagePrelude;
        }

        public ExceptionMessageCollector add(Throwable throwable) {
            exceptionsOccurred = true;
            combinedMessage += "\n" + throwable.getMessage();
            return this;
        }
    }

    public static class TensorValidationFailed extends RuntimeException {
        public TensorValidationFailed(String message) {
            super(message);
        }
    }

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        final ApplicationPackage applicationPackage = deployState.getApplicationPackage();
        final ExceptionMessageCollector exceptionMessageCollector = new ExceptionMessageCollector("Failed to validate constant tensor file(s):");

        for (SearchDefinition sd : deployState.getSearchDefinitions()) {
            for (RankingConstant rc : sd.getSearch().getRankingConstants()) {
                try {
                    validateRankingConstant(rc, applicationPackage);
                } catch (InvalidConstantTensor | FileNotFoundException e) {
                    exceptionMessageCollector.add(e);
                }
            }
        }

        if (exceptionMessageCollector.exceptionsOccurred) {
            throw new TensorValidationFailed(exceptionMessageCollector.combinedMessage);
        }
    }

    public void validateRankingConstant(RankingConstant rankingConstant, ApplicationPackage applicationPackage) throws FileNotFoundException {
        final ApplicationFile tensorApplicationFile = applicationPackage.getFile(Path.fromString(rankingConstant.getFileName()));
        final Reader tensorReader = tensorApplicationFile.createReader();

        ConstantTensorJsonValidator tensorValidator = new ConstantTensorJsonValidator(tensorReader, rankingConstant.getTensorType());
        tensorValidator.validate();
    }
}