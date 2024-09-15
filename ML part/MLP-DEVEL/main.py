from sklearn.model_selection import train_test_split
from sklearn.neural_network import MLPClassifier
from sklearn.model_selection import GridSearchCV
from sklearn.metrics import accuracy_score
from pandas import read_csv
from numpy import ravel
import json
import joblib

### FOR TESTING ONLY ###

## INGESTION
# combine 4 .csv Datasets
data = read_csv('data/preprocessedDataset.csv')

## REMOVE WRONG SAMPLES
# calculate average on the sample - discard samples with low values

## SEGREGATION
training_data, testing_data, training_labels, testing_labels = train_test_split(data.iloc[:, 4:len(data.columns)], data.iloc[:, 1])  # return DataFrame obj (pandas)

## DEVELOPMENT
mlp = MLPClassifier(random_state=1234)
parameters = {'max_iter': (100, 200)}
gs = GridSearchCV(mlp, parameters)
gs.fit(training_data, ravel(training_labels))

# EXECUTE THE CLASSIFIER
print('TRAIN DATA\n', training_data)
print('TEST DATA\n', testing_data)
inference = gs.predict(testing_data)
score = accuracy_score(ravel(testing_labels), inference)
print('deploy score:', score)
print('inference\n', inference)

joblib.dump(gs, 'fitted_model.sav')

## CONVERT MODEL TO .pmml
